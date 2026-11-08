package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.enrichment.*;
import com.roomelephant.porthole.core.mapping.ContainerMapper;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.inbound.GetContainers;
import com.roomelephant.porthole.fleet.*;
import java.util.*;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@NullMarked
public class ContainerService implements GetContainers {
    private static final Logger log = LoggerFactory.getLogger(ContainerService.class);

    private static final String SELF_ICON = "porthole-mark.svg";

    private final Fleet fleet;
    private final ContainerMapper mapper;
    private final ContainerEnrichment enrichment;
    private final SelfContainer self;

    public ContainerService(Fleet fleet, ContainerMapper mapper, ContainerEnrichment enrichment, SelfContainer self) {
        this.fleet = fleet;
        this.mapper = mapper;
        this.enrichment = enrichment;
        this.self = self;
    }

    @Override
    public ContainerInventory getContainers(ContainerQuery query) {
        List<Result> results =
                fleet.snapshots().stream().map(result -> present(result, query)).toList();
        return new ContainerInventory(
                results.stream().map(Result::source).toList(),
                results.stream().flatMap(r -> r.containers().stream()).toList());
    }

    private Result present(SourceSnapshot snapshot, ContainerQuery query) {
        Source source = snapshot.source();
        if (snapshot.containers().isEmpty()) return new Result(state(snapshot), List.of());
        try {
            List<Container> mapped = mapper.toModels(snapshot.containers(), source.id());
            EnrichmentContext context = new EnrichmentContext(
                    source.id(), source.publishedHost(), query.includeWithoutEndpoints(), query.includeStopped());
            List<Container> containers = enrichment.enrich(mapped, context);
            containers.forEach(c -> markSelf(c, source.local()));
            return new Result(state(snapshot), containers);
        } catch (RuntimeException e) {
            log.warn("Could not prepare containers from Docker source {}", source.id(), e);
            return new Result(
                    new SourceState(
                            source.id(),
                            source.name(),
                            SourceStatus.ERROR,
                            "Could not retrieve containers",
                            snapshot.lastSuccessAt()),
                    List.of());
        }
    }

    private static SourceState state(SourceSnapshot snapshot) {
        return new SourceState(
                snapshot.source().id(),
                snapshot.source().name(),
                SourceStatus.valueOf(snapshot.status().name()),
                snapshot.error(),
                snapshot.lastSuccessAt());
    }

    private void markSelf(Container container, boolean local) {
        boolean matches = self.matches(container.id(), local);
        container.setSelf(matches);
        if (matches && container.preferences().icon() == null) {
            container.setIconUrl(SELF_ICON);
        }
    }

    private record Result(SourceState source, List<Container> containers) {}
}
