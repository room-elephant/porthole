package com.roomelephant.porthole.domain.service;

import com.github.dockerjava.api.DockerClient;
import com.roomelephant.porthole.domain.component.ContainerEnrichment;
import com.roomelephant.porthole.domain.component.endpoint.EndpointContextResolver;
import com.roomelephant.porthole.domain.mapper.ContainerMapper;
import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.exception.DockerUnavailableException;
import com.roomelephant.porthole.domain.model.exception.UnexpectedException;
import java.net.SocketException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NullMarked
public class ContainerService {

    private final DockerClient dockerClient;
    private final ContainerMapper containerMapper;
    private final ContainerEnrichment containerEnrichment;
    private final EndpointContextResolver endpointContextResolver;

    public ContainerService(
            DockerClient dockerClient,
            ContainerMapper containerMapper,
            ContainerEnrichment containerEnrichment,
            EndpointContextResolver endpointContextResolver) {
        this.dockerClient = dockerClient;
        this.containerMapper = containerMapper;
        this.containerEnrichment = containerEnrichment;
        this.endpointContextResolver = endpointContextResolver;
    }

    public @NonNull List<Container> getContainers(
            boolean includeWithoutEndpoints, boolean includeStopped, String requestHostname) {
        var context = endpointContextResolver.resolve(requestHostname);
        List<com.github.dockerjava.api.model.Container> containers;
        try {
            containers =
                    dockerClient.listContainersCmd().withShowAll(includeStopped).exec();
        } catch (RuntimeException e) {
            if (isDockerConnectionError(e)) {
                throw new DockerUnavailableException(e);
            }
            throw new UnexpectedException(e);
        }

        return containers.stream()
                .map(containerMapper::toModel)
                .map(container -> containerEnrichment.enrich(container, context))
                .filter(container ->
                        includeWithoutEndpoints || !container.urlCandidates().isEmpty())
                .toList();
    }

    private boolean isDockerConnectionError(RuntimeException e) {
        return e.getCause() instanceof SocketException;
    }
}
