package com.roomelephant.porthole.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

public record ContainerDTO(
        String id,
        String name,
        String displayName,
        String image,
        Set<Integer> exposedPorts,
        String iconUrl,
        String project,
        String state,
        String status) {
    @JsonProperty("hasPublicPorts")
    public boolean hasPublicPorts() {
        return exposedPorts != null && !exposedPorts.isEmpty();
    }
}
