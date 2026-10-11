package com.roomelephant.porthole.domain.model;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record ContainerSnapshot(
        String id,
        String name,
        String image,
        Set<Integer> publishedPorts,
        Map<String, String> labels,
        @Nullable String state,
        @Nullable String status) {
    public ContainerSnapshot {
        publishedPorts = Set.copyOf(publishedPorts);
        labels = Map.copyOf(labels);
    }
}
