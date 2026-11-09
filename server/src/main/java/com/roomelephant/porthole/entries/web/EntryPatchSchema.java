package com.roomelephant.porthole.entries.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Describes entry bodies in the OpenAPI document only. Requests are read as JSON by {@link EntryPatchReader}, since a
 * merge patch must tell a missing field from null and a Java type can't, so nothing here is ever deserialized.
 */
@NullMarked
@Schema(
        name = "EntryPatch",
        description = "JSON Merge Patch of an entry. A missing field is kept; null removes the saved value.")
interface EntryPatchSchema {
    @Schema(description = "Name shown on the tile, 1–255 characters. Required for links.")
    @Nullable
    String getName();

    @Schema(description = "Stack the link belongs to; blank or null for none. Links only.")
    @Nullable
    String getStack();

    @Schema(implementation = IconChoice.class)
    @Nullable
    Object getIcon();

    @Schema(
            name = "IconChoice",
            description = "Icon choice",
            oneOf = {LibraryIcon.class, UploadedIcon.class})
    interface IconChoice {}

    @Schema(
            description = "Endpoint choice. A link needs a URL; a discovered candidate or none is for containers.",
            oneOf = {DiscoveredEndpoint.class, CustomEndpoint.class, NoEndpoint.class})
    @Nullable
    Object getEndpoint();

    @Schema(name = "LibraryIcon", description = "An icon from the icon library", example = "{\"library\": \"router\"}")
    interface LibraryIcon {
        @Schema(description = "Library icon name", requiredMode = Schema.RequiredMode.REQUIRED)
        String getLibrary();
    }

    @Schema(name = "UploadedIcon", description = "An uploaded icon")
    interface UploadedIcon {
        @Schema(description = "ID returned by POST /api/icons", requiredMode = Schema.RequiredMode.REQUIRED)
        String getUpload();
    }

    @Schema(name = "DiscoveredEndpoint", description = "One of the container's discovered endpoints")
    interface DiscoveredEndpoint {
        @Schema(description = "Candidate ID from the entry's endpoints", requiredMode = Schema.RequiredMode.REQUIRED)
        String getId();
    }

    @Schema(name = "CustomEndpoint", description = "An address of your own")
    interface CustomEndpoint {
        @Schema(description = "Absolute HTTP(S) URL without credentials", requiredMode = Schema.RequiredMode.REQUIRED)
        String getUrl();
    }

    @Schema(
            name = "NoEndpoint",
            type = "string",
            allowableValues = {"none"},
            description = "The container has no web UI")
    interface NoEndpoint {}
}
