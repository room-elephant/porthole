package com.roomelephant.porthole.core.port.outbound;

import com.roomelephant.porthole.core.model.*;
import java.util.List;
import org.jspecify.annotations.*;

/** Source-aware Docker operations. No client handles cross this boundary. */
@NullMarked
public interface DockerOperations {
    List<DockerSource> sources();

    DockerSource requireSource(String sourceId);

    List<ContainerSnapshot> listContainers(String sourceId);

    ContainerInspection inspectContainer(ContainerKey key);

    @Nullable
    List<String> getRepoDigests(String sourceId, @Nullable String imageId);

    void ping(String sourceId);
}
