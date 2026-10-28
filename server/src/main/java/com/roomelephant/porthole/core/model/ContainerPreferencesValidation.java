package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ContainerPreferencesValidation {
    private ContainerPreferencesValidation() {}

    public static void validate(ContainerPreferencesKey key, @Nullable SelectedEndpoint selection) {
        requireNonblank(key.sourceId(), "sourceId");
        requireNonblank(key.containerName(), "containerName");
        if (selection == null) {
            return;
        }
        requireNonblank(selection.id(), "selectedEndpoint.id");
        requireNonblank(selection.source(), "selectedEndpoint.source");
        if ("custom".equals(selection.id()) || "custom".equals(selection.source())) {
            if (!selection.hasValidCustomUrl()) {
                throw new IllegalArgumentException(
                        "Custom selection requires id/source custom and an absolute HTTP(S) URL without credentials");
            }
        } else if ("none".equals(selection.id()) || "none".equals(selection.source())) {
            if (!"none".equals(selection.id()) || !"none".equals(selection.source()) || selection.url() != null) {
                throw new IllegalArgumentException("No web UI selection requires id/source none and no URL");
            }
        } else if (selection.url() != null) {
            throw new IllegalArgumentException("Discovered selections must not store a URL");
        }
    }

    private static void requireNonblank(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
