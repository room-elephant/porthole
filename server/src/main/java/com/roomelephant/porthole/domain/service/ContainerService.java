package com.roomelephant.porthole.domain.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.roomelephant.porthole.domain.mapper.ContainerMapper;
import com.roomelephant.porthole.domain.model.ContainerDTO;
import com.roomelephant.porthole.domain.model.EndpointContext;
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

    public ContainerService(DockerClient dockerClient, ContainerMapper containerMapper) {
        this.dockerClient = dockerClient;
        this.containerMapper = containerMapper;
    }

    public @NonNull List<ContainerDTO> getContainers(
            boolean includeWithoutEndpoints, boolean includeStopped, EndpointContext context) {
        List<Container> containers;
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
                .map(container -> containerMapper.toDTO(container, context))
                .filter(dto -> includeWithoutEndpoints || dto.hasEndpoints())
                .toList();
    }

    private boolean isDockerConnectionError(RuntimeException e) {
        return e.getCause() instanceof SocketException;
    }
}
