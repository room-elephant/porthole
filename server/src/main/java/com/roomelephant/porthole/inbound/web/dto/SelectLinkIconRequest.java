package com.roomelephant.porthole.inbound.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding(SelectLinkIconRequest.class)
@Schema(description = "Use a Dashboard Icons library icon for a link")
public record SelectLinkIconRequest(
        @Schema(
                description = "Dashboard Icons library icon name",
                example = "router",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name) {}
