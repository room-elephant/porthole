package com.roomelephant.porthole.entries.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import tools.jackson.databind.JsonNode;

@NullMarked
@RegisterReflectionForBinding(CreateLinkRequest.class)
@Schema(description = "A new link")
public record CreateLinkRequest(
        @Schema(description = "Name shown on the tile, 1–255 characters", requiredMode = Schema.RequiredMode.REQUIRED)
        @Nullable
        String name,

        @Schema(description = "Absolute HTTP(S) URL without credentials", requiredMode = Schema.RequiredMode.REQUIRED)
        @Nullable
        String url,

        @Schema(description = "Stack the link belongs to; blank or null for none") @Nullable
        String stack,

        @Schema(implementation = EntryPatchSchema.IconChoice.class) @Nullable
        JsonNode icon) {}
