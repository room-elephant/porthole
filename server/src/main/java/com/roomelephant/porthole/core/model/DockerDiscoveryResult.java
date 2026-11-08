package com.roomelephant.porthole.core.model;

import com.roomelephant.porthole.fleet.*;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/** Snapshots and retrieval status for one configured Docker source. */
@NullMarked
public record DockerDiscoveryResult(Source source, SourceState state, List<ContainerSnapshot> containers) {
    public DockerDiscoveryResult {
        containers = List.copyOf(containers);
    }
}
