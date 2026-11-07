package com.roomelephant.porthole.inbound.web.dto;

import com.roomelephant.porthole.core.model.ExternalLinkDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding(LinkRequest.class)
@Schema(description = "A link's name, URL and stack. On an update, replaces all three.")
public record LinkRequest(
        @Schema(description = "Name shown on the tile", example = "Router", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(
                description = "Absolute http or https URL without credentials",
                example = "http://192.168.50.1/",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String url,

        @Schema(description = "Stack the link belongs to. Blank or null means none.", example = "base") @Nullable
        String stack) {
    public ExternalLinkDetails toDetails() {
        return new ExternalLinkDetails(name, url, stack);
    }
}
