package com.roomelephant.porthole.inbound.web.dto;

import com.roomelephant.porthole.core.model.PresentedLink;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding(LinkDTO.class)
@Schema(description = "A page the user added to the dashboard")
public record LinkDTO(
        @Schema(description = "Generated link ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,

        @Schema(description = "Name shown on the tile", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Absolute http or https URL", requiredMode = Schema.RequiredMode.REQUIRED)
        String url,

        @Schema(description = "Stack the link belongs to, or null") @Nullable
        String stack,

        @Schema(description = "URL of the chosen icon, or null when none was chosen") @Nullable
        String iconUrl) {
    public static LinkDTO from(PresentedLink link) {
        return new LinkDTO(link.id(), link.name(), link.url(), link.stack(), link.iconUrl());
    }
}
