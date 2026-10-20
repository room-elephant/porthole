package com.roomelephant.porthole.inbound.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomelephant.porthole.core.model.VersionInfo;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding({ContainerDTO.class, EndpointDTO.class, VersionInfo.class})
public record ContainerDTO(
        String id,
        String sourceId,
        String name,
        String displayName,
        String image,
        List<EndpointDTO> endpoints,
        String iconUrl,
        @Nullable String project,
        @Nullable String state,
        @Nullable String status,
        @Nullable VersionInfo version,
        boolean self) {
    public ContainerDTO {
        endpoints = List.copyOf(endpoints);
    }

    @JsonProperty("hasEndpoints")
    public boolean hasEndpoints() {
        return !endpoints.isEmpty();
    }
}
