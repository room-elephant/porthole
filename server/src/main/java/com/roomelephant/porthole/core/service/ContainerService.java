package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.enrichment.*;
import com.roomelephant.porthole.core.mapping.ContainerMapper;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.inbound.GetContainers;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@NullMarked
public class ContainerService implements GetContainers {
    private final DockerDiscoveryService discovery;
    private final ContainerMapper mapper;
    private final ContainerEnrichment enrichment;
    private final SelfContainer self;

    public ContainerService(
            DockerDiscoveryService discovery,
            ContainerMapper mapper,
            ContainerEnrichment enrichment,
            SelfContainer self) {
        this.discovery = discovery;
        this.mapper = mapper;
        this.enrichment = enrichment;
        this.self = self;
    }

    @Override
    public ContainerInventory getContainers(ContainerQuery query) {
        List<Result> results = discovery.discover().stream()
                .map(result -> present(result, query))
                .toList();
        return new ContainerInventory(
                results.stream().map(Result::source).toList(),
                results.stream().flatMap(r -> r.containers().stream()).toList());
    }

    private Result present(DockerDiscoveryResult result, ContainerQuery query) {
        if (result.state().status() != SourceStatus.UP) return new Result(result.state(), List.of());
        DockerSource source = result.source();
        try {
            List<Container> mapped = mapper.toModels(result.containers(), source.id());
            EnrichmentContext context = EnrichmentContext.forContainers(
                    source.id(),
                    source.publishedHost(),
                    query.includeWithoutEndpoints(),
                    query.includeStopped(),
                    mapped);
            List<Container> containers = mapped.stream()
                    .map(c -> enrichment.enrich(c, context))
                    .flatMap(Optional::stream)
                    .toList();
            containers.forEach(c -> c.setSelf(self.matches(c.id(), source.local())));
            return new Result(result.state(), containers);
        } catch (RuntimeException e) {
            log.warn("Could not prepare containers from Docker source {}", source.id(), e);
            return new Result(
                    new SourceState(source.id(), source.name(), SourceStatus.ERROR, "Could not retrieve containers"),
                    List.of());
        }
    }

    private record Result(SourceState source, List<Container> containers) {}
}
