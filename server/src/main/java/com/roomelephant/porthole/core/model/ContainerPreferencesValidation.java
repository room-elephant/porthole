package com.roomelephant.porthole.core.model;

import static com.roomelephant.porthole.core.model.SelectedEndpoint.CUSTOM;
import static com.roomelephant.porthole.core.model.SelectedEndpoint.NONE;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ContainerPreferencesValidation {
    static final int MAX_IDENTIFIER_LENGTH = 255;

    private ContainerPreferencesValidation() {}

    public static void validate(ContainerPreferencesKey key, @Nullable SelectedEndpoint selection) {
        requireIdentifier(key.sourceId(), "sourceId");
        requireIdentifier(key.containerName(), "containerName");
        if (selection == null) {
            return;
        }
        requireIdentifier(selection.id(), "selectedEndpoint.id");
        requireIdentifier(selection.source(), "selectedEndpoint.source");
        if (CUSTOM.equals(selection.id()) || CUSTOM.equals(selection.source())) {
            if (!selection.hasValidCustomUrl()) {
                throw new IllegalArgumentException(
                        "Custom selection requires id/source custom and an absolute HTTP(S) URL"
                                + " without credentials, at most " + SelectedEndpoint.MAX_URL_LENGTH + " characters");
            }
        } else if (NONE.equals(selection.id()) || NONE.equals(selection.source())) {
            if (!NONE.equals(selection.id()) || !NONE.equals(selection.source()) || selection.url() != null) {
                throw new IllegalArgumentException("No web UI selection requires id/source none and no URL");
            }
        } else if (selection.url() != null) {
            throw new IllegalArgumentException("Discovered selections must not store a URL");
        }
    }

    public static void validateIcon(ContainerPreferencesKey key, SelectedIcon icon) {
        requireIdentifier(key.sourceId(), "sourceId");
        requireIdentifier(key.containerName(), "containerName");
        @Nullable String name = icon.name();
        if (name == null) {
            throw new IllegalArgumentException("icon.name must not be blank");
        }
        if (SelectedIcon.LIBRARY.equals(icon.type())) {
            if (!SelectedIcon.LIBRARY_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid library icon name");
            }
        } else if (SelectedIcon.UPLOAD.equals(icon.type())) {
            if (!SelectedIcon.UPLOAD_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid uploaded icon name");
            }
        } else {
            throw new IllegalArgumentException("icon.type must be library or upload");
        }
    }

    private static void requireIdentifier(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(field + " must be at most " + MAX_IDENTIFIER_LENGTH + " characters");
        }
    }
}
