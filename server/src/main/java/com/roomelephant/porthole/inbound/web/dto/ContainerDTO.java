package com.roomelephant.porthole.inbound.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import io.swagger.v3.oas.annotations.media.Schema;
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
        boolean self,

        @Schema(
                description = "Whether the container has a web UI, including unresolved endpoint choices",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasWebUi) {
    public ContainerDTO {
        endpoints = List.copyOf(endpoints);
    }

    @JsonProperty("entryId")
    @Schema(description = "Entry ID, for PATCH /api/entries/{id}", requiredMode = Schema.RequiredMode.REQUIRED)
    public String entryId() {
        return new ContainerRef(sourceId, name).id();
    }

    @JsonProperty("hasEndpoints")
    public boolean hasEndpoints() {
        return !endpoints.isEmpty();
    }
}
