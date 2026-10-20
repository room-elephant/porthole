package com.roomelephant.porthole.outbound.docker;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerConfig;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.model.exception.*;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import java.util.*;
import org.jspecify.annotations.*;

@NullMarked
public final class DockerJavaOperations implements DockerOperations {
    private final DockerSourceRegistry sources;
    private final DockerContainerMapper mapper = new DockerContainerMapper();

    public DockerJavaOperations(DockerSourceRegistry sources) {
        this.sources = sources;
    }

    @Override
    public List<DockerSource> sources() {
        return sources.sources().stream().map(this::descriptor).toList();
    }

    private DockerSource descriptor(DockerConnection s) {
        return new DockerSource(
                s.id(), s.name(), s.publishedHost(), s.connectionTimeout(), s.responseTimeout(), s.local());
    }

    @Override
    public List<ContainerSnapshot> listContainers(String id) {
        try {
            return sources.require(id).client().listContainersCmd().withShowAll(true).exec().stream()
                    .map(mapper::toSnapshot)
                    .toList();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public ContainerInspection inspectContainer(ContainerKey key) {
        try {
            InspectContainerResponse raw = sources.require(key.sourceId())
                    .client()
                    .inspectContainerCmd(key.containerId())
                    .exec();
            ContainerConfig config = raw.getConfig();
            return new ContainerInspection(
                    config == null ? null : config.getImage(),
                    raw.getImageId(),
                    config == null || config.getEnv() == null ? List.of() : Arrays.asList(config.getEnv()),
                    config == null || config.getLabels() == null ? Map.of() : config.getLabels());
        } catch (com.github.dockerjava.api.exception.NotFoundException _) {
            throw new NotFoundException(key.containerId());
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public @Nullable List<String> getRepoDigests(String sourceId, @Nullable String imageId) {
        try {
            return sources.require(sourceId)
                    .client()
                    .inspectImageCmd(imageId)
                    .exec()
                    .getRepoDigests();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public void ping(String id) {
        try {
            sources.require(id).client().pingCmd().exec();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    private RuntimeException translate(RuntimeException e) {
        return DockerFailures.isTransportFailure(e) ? new DockerUnavailableException(e) : new UnexpectedException(e);
    }
}
