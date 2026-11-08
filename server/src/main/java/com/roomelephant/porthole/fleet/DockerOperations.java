package com.roomelephant.porthole.fleet;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Source-aware Docker operations. No client handles cross this boundary. */
@NullMarked
public interface DockerOperations {
    List<ContainerSnapshot> listContainers(String sourceId);

    ContainerInspection inspectContainer(ContainerKey key);

    @Nullable
    List<String> getRepoDigests(String sourceId, @Nullable String imageId);

    /** Streams the source's container events until the returned handle is closed or the stream ends. */
    AutoCloseable watchContainerEvents(String sourceId, ContainerEventListener listener);
}
