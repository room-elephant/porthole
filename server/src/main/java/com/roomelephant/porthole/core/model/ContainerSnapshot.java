package com.roomelephant.porthole.core.model;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.*;

/** Docker metadata before presentation and request-specific enrichment. */
@NullMarked
public record ContainerSnapshot(
        @Nullable String id,
        String name,
        @Nullable String image,
        Set<Integer> publishedPorts,
        Map<String, String> labels,
        @Nullable String state,
        @Nullable String status) {
    public ContainerSnapshot {
        publishedPorts = Set.copyOf(publishedPorts);
        labels = Map.copyOf(labels);
    }
}
