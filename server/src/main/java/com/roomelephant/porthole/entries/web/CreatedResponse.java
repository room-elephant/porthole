package com.roomelephant.porthole.entries.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding(CreatedResponse.class)
@Schema(description = "The ID of what was created")
public record CreatedResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id) {}
