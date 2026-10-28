package com.roomelephant.porthole.inbound.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Schema(description = "Shared container preferences included in the inventory")
public record ContainerPreferencesDTO(
        @JsonProperty(value = "selectedEndpoint", required = true)
        @Schema(
                description = "Saved endpoint choice; explicit null restores automatic selection",
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @Nullable
        SelectedEndpointDTO selectedEndpoint) {}
