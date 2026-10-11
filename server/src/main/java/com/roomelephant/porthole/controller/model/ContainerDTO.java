package com.roomelephant.porthole.controller.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding({ContainerDTO.class, EndpointDTO.class})
public record ContainerDTO(
        String id,
        String name,
        String displayName,
        String image,
        List<EndpointDTO> endpoints,
        String iconUrl,
        @Nullable String project,
        @Nullable String state,
        @Nullable String status) {
    public ContainerDTO {
        endpoints = List.copyOf(endpoints);
    }

    @JsonProperty("hasEndpoints")
    public boolean hasEndpoints() {
        return !endpoints.isEmpty();
    }
}
