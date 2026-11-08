package com.roomelephant.porthole.endpoints;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class EndpointFixtures {
    static EndpointTarget target(Set<Integer> ports, Map<String, String> labels) {
        return at(ports, labels, "192.168.50.6");
    }

    static EndpointTarget at(Set<Integer> ports, Map<String, String> labels, @Nullable String publishedHost) {
        return new EndpointTarget("transmission", ports, labels, publishedHost);
    }

    static EndpointTarget at(EndpointTarget target, @Nullable String publishedHost) {
        return new EndpointTarget(target.containerName(), target.publishedPorts(), target.labels(), publishedHost);
    }
}
