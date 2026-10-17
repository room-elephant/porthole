package com.roomelephant.porthole.domain.component.endpoint;

import com.roomelephant.porthole.domain.model.Container;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EndpointFixtures {
    static Container container(Set<Integer> ports, Map<String, String> labels) {
        return new Container(
                "id",
                "local",
                "transmission",
                "transmission",
                "transmission:latest",
                ports,
                labels,
                List.of(),
                "icon",
                null,
                "running",
                "Up");
    }
}
