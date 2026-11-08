package com.roomelephant.porthole.fleet;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.*;

/** Docker metadata as listed, before presentation. */
@NullMarked
public record ContainerSnapshot(
        @Nullable String id,
        String name,
        @Nullable String image,
        @Nullable String imageId,
        Set<Integer> publishedPorts,
        Map<String, String> labels,
        @Nullable String state,
        @Nullable String status) {
    public ContainerSnapshot {
        publishedPorts = Set.copyOf(publishedPorts);
        labels = Map.copyOf(labels);
    }
}
