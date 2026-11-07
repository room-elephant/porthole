package com.roomelephant.porthole.inbound.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding(SelectContainerIconRequest.class)
@Schema(
        description =
                "Use a Dashboard Icons library icon for a container, including temporarily unavailable containers")
public record SelectContainerIconRequest(
        @Schema(description = "Docker connection source ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceId,

        @Schema(
                description = "Docker container name, independent of runtime ID or display name",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String containerName,

        @Schema(
                description = "Dashboard Icons library icon name",
                example = "coredns",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name) {}
