package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.ContainerPreferencesValidation;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.port.inbound.SelectContainerIcon;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import com.roomelephant.porthole.fleet.SourceCatalog;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@NullMarked
@Service
public class ContainerIconService implements SelectContainerIcon {
    private final ContainerPreferencesStore preferences;
    private final SourceCatalog sources;
    private final UploadedIcons uploads;

    public ContainerIconService(ContainerPreferencesStore preferences, SourceCatalog sources, UploadedIcons uploads) {
        this.preferences = preferences;
        this.sources = sources;
        this.uploads = uploads;
    }

    @Override
    public void selectLibraryIcon(ContainerPreferencesKey key, String name) {
        SelectedIcon icon = SelectedIcon.library(name);
        ContainerPreferencesValidation.validateIcon(key, icon);
        requireConfiguredSource(key);
        uploads.saveLibrary(icon, () -> currentIcon(key), selected -> preferences.replaceIcon(key, selected));
    }

    @Override
    public void uploadIcon(ContainerPreferencesKey key, byte[] bytes) {
        ContainerPreferencesValidation.validate(key, null);
        requireConfiguredSource(key);
        uploads.saveUpload(bytes, () -> currentIcon(key), selected -> preferences.replaceIcon(key, selected));
    }

    private void requireConfiguredSource(ContainerPreferencesKey key) {
        if (!sources.contains(key.sourceId())) {
            throw new IllegalArgumentException("Unknown sourceId: " + key.sourceId());
        }
    }

    private @Nullable SelectedIcon currentIcon(ContainerPreferencesKey key) {
        @Nullable ContainerPreferences current = preferences.snapshot().get(key);
        return current == null ? null : current.icon();
    }
}
