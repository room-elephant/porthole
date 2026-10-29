package com.roomelephant.porthole.inbound.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding(EndpointDTO.class)
@Schema(description = "Resolved service endpoint candidate, including the saved custom URL while configured")
public record EndpointDTO(
        String id,
        String source,
        String url,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description =
                        "Whether this is the effective endpoint for navigation; at most one candidate is selected")
        boolean selected) {}
