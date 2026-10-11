package com.roomelephant.porthole.domain.component.endpoint;

import com.roomelephant.porthole.domain.model.ContainerSnapshot;
import java.util.Map;
import java.util.Set;

final class EndpointFixtures {
    static ContainerSnapshot container(Set<Integer> ports, Map<String, String> labels) {
        return new ContainerSnapshot("id", "transmission", "transmission:latest", ports, labels, "running", "Up");
    }
}
