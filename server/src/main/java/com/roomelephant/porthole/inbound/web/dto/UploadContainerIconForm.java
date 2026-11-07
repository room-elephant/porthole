package com.roomelephant.porthole.inbound.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;

/** Documents the multipart upload form; the controller binds its fields individually. */
@NullMarked
@Schema(description = "Upload an icon for a container, including temporarily unavailable containers")
public record UploadContainerIconForm(
        @Schema(description = "Docker connection source ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceId,

        @Schema(
                description = "Docker container name, independent of runtime ID or display name",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String containerName,

        @Schema(
                description = "PNG, WebP, JPEG or SVG image of at most 1 MB",
                type = "string",
                format = "binary",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String file) {}
