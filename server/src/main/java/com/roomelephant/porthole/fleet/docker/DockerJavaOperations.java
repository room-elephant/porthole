package com.roomelephant.porthole.fleet.docker;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.EventType;
import com.roomelephant.porthole.fleet.*;
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
            throw new ContainerNotFoundException(key.containerId());
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
    public AutoCloseable watchContainerEvents(String sourceId, ContainerEventListener listener) {
        try {
            return sources.require(sourceId)
                    .client()
                    .eventsCmd()
                    .withEventTypeFilter(EventType.CONTAINER)
                    .exec(new ResultCallback.Adapter<Event>() {
                        @Override
                        public void onNext(Event event) {
                            if (changesListing(event)) listener.onEvent();
                        }

                        @Override
                        public void onError(Throwable failure) {
                            listener.onClosed(failure);
                        }

                        @Override
                        public void onComplete() {
                            listener.onClosed(null);
                        }
                    });
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    private RuntimeException translate(RuntimeException e) {
        return DockerFailures.isTransportFailure(e)
                ? new DockerUnavailableException(e)
                : new DockerOperationException(e);
    }

    /**
     * Health checks run as execs, so a container with a health check emits exec events every interval. Those, and
     * other actions that leave the container's listing as it was, must not trigger a listing. Unknown actions do.
     */
    static boolean changesListing(Event event) {
        String action = event.getAction() != null ? event.getAction() : event.getStatus();
        if (action == null) return true;
        String name = action.split(":", 2)[0].trim();
        return !name.startsWith("exec_") && !LISTING_NEUTRAL.contains(name);
    }

    private static final Set<String> LISTING_NEUTRAL =
            Set.of("attach", "detach", "resize", "top", "export", "commit", "copy", "archive-path", "extract-to-dir");
}
