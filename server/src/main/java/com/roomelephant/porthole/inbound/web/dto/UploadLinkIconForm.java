package com.roomelephant.porthole.inbound.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;

/** Documents the multipart upload form; the controller binds the file itself. */
@NullMarked
@Schema(description = "Upload an icon for a link")
public record UploadLinkIconForm(
        @Schema(
                description = "PNG, WebP, JPEG or SVG image of at most 1 MB",
                type = "string",
                format = "binary",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String file) {}
