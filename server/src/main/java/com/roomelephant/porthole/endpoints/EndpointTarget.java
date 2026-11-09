package com.roomelephant.porthole.endpoints;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What discovery needs to know about one container.
 *
 * @param publishedHost the source's address for published ports, or null for the default
 */
@NullMarked
public record EndpointTarget(
        String containerName,
        Set<Integer> publishedPorts,
        Map<String, String> labels,
        @Nullable String publishedHost) {
    public EndpointTarget {
        publishedPorts = Set.copyOf(publishedPorts);
        labels = Map.copyOf(labels);
    }
}
