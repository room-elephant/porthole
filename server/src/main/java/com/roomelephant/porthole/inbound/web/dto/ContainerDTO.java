package com.roomelephant.porthole.inbound.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.core.model.VersionInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding({
    ContainerDTO.class,
    EndpointDTO.class,
    VersionInfo.class,
    ContainerPreferencesDTO.class,
    SelectedEndpointDTO.class
})
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
        boolean self,

        @Schema(description = "Shared preferences, with a null selectedEndpoint when automatic selection is enabled")
        ContainerPreferencesDTO preferences) {
    public ContainerDTO {
        endpoints = List.copyOf(endpoints);
    }

    @JsonProperty("hasEndpoints")
    public boolean hasEndpoints() {
        var selection = preferences.selectedEndpoint();
        return !endpoints.isEmpty()
                || (selection != null
                        && new SelectedEndpoint(selection.id(), selection.source(), selection.url())
                                .hasValidCustomUrl());
    }
}
