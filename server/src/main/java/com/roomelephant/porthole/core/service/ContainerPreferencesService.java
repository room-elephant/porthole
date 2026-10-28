package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.core.port.inbound.SelectContainerEndpoint;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@NullMarked
@Service
public class ContainerPreferencesService implements SelectContainerEndpoint {
    private final ContainerPreferencesStore store;

    public ContainerPreferencesService(ContainerPreferencesStore store) {
        this.store = store;
    }

    @Override
    public ContainerPreferences select(ContainerPreferencesKey key, @Nullable SelectedEndpoint selection) {
        requireNonblank(key.sourceId(), "sourceId");
        requireNonblank(key.containerName(), "containerName");
        if (selection != null) {
            validateSelection(selection);
        }
        return store.replaceSelection(key, selection);
    }

    private void validateSelection(SelectedEndpoint selection) {
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

    private void requireNonblank(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
