package com.roomelephant.porthole.inbound.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Schema(description = "Saved endpoint identity. Custom and none are reserved ID/source pairs.")
public record SelectedEndpointDTO(
        @Schema(description = "Discovered endpoint ID, custom, or none", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,

        @Schema(description = "Endpoint provider, custom, or none", requiredMode = Schema.RequiredMode.REQUIRED)
        String source,

        @Schema(
                description = "Absolute HTTP(S) URL without credentials, required only for a custom selection",
                nullable = true)
        @Nullable
        String url) {}
