package com.roomelephant.porthole.core.model;

import java.util.List;
import org.jspecify.annotations.NullMarked;

/** Snapshots and retrieval status for one configured Docker source. */
@NullMarked
public record DockerDiscoveryResult(DockerSource source, SourceState state, List<ContainerSnapshot> containers) {
    public DockerDiscoveryResult {
        containers = List.copyOf(containers);
    }
}
