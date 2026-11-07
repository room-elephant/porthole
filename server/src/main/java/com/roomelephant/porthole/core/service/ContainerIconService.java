package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.ContainerPreferencesValidation;
import com.roomelephant.porthole.core.model.IconImageType;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.model.UploadedIcon;
import com.roomelephant.porthole.core.model.exception.IconLimitReachedException;
import com.roomelephant.porthole.core.port.inbound.ReadUploadedIcon;
import com.roomelephant.porthole.core.port.inbound.SelectContainerIcon;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import com.roomelephant.porthole.core.port.outbound.IconFileStore;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@NullMarked
@Service
public class ContainerIconService implements SelectContainerIcon, ReadUploadedIcon {
    static final int MAX_UPLOADS = 500;

    private static final Logger log = LoggerFactory.getLogger(ContainerIconService.class);

    private final ContainerPreferencesStore preferences;
    private final DockerOperations sources;
    private final IconFileStore files;

    public ContainerIconService(ContainerPreferencesStore preferences, DockerOperations sources, IconFileStore files) {
        this.preferences = preferences;
        this.sources = sources;
        this.files = files;
    }

    @Override
    public synchronized void selectLibraryIcon(ContainerPreferencesKey key, String name) {
        SelectedIcon icon = SelectedIcon.library(name);
        validate(key, icon);
        @Nullable SelectedIcon previous = currentIcon(key);
        preferences.replaceIcon(key, icon);
        deleteUpload(previous);
    }

    @Override
    public synchronized void uploadIcon(ContainerPreferencesKey key, byte[] bytes) {
        IconImageType type = IconImageType.detect(bytes)
                .orElseThrow(() -> new IllegalArgumentException("Icon must be a PNG, WebP or JPEG image"));
        ContainerPreferencesValidation.validate(key, null);
        requireConfiguredSource(key);
        @Nullable SelectedIcon previous = currentIcon(key);
        int replaced = previous != null && previous.selectsUpload() ? 1 : 0;
        if (files.count() - replaced >= MAX_UPLOADS) {
            throw new IconLimitReachedException(MAX_UPLOADS);
        }
        String name = files.store(bytes, type);
        try {
            preferences.replaceIcon(key, SelectedIcon.upload(name));
        } catch (RuntimeException e) {
            deleteQuietly(name);
            throw e;
        }
        deleteUpload(previous);
    }

    @Override
    public Optional<UploadedIcon> read(String name) {
        if (!SelectedIcon.UPLOAD_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        return IconImageType.fromFileName(name)
                .flatMap(type -> files.read(name).map(bytes -> new UploadedIcon(bytes, type.contentType())));
    }

    /** Best-effort removal of uploads no preference references, which a crash between storing and saving can leave. */
    public synchronized void removeUnreferencedUploads() {
        Set<String> referenced = preferences.snapshot().values().stream()
                .map(ContainerPreferences::icon)
                .filter(Objects::nonNull)
                .filter(SelectedIcon::selectsUpload)
                .map(SelectedIcon::name)
                .collect(Collectors.toSet());
        try {
            files.retainOnly(referenced);
        } catch (RuntimeException e) {
            log.warn("Could not remove unreferenced uploaded icons", e);
        }
    }

    private void validate(ContainerPreferencesKey key, SelectedIcon icon) {
        ContainerPreferencesValidation.validateIcon(key, icon);
        requireConfiguredSource(key);
    }

    private void requireConfiguredSource(ContainerPreferencesKey key) {
        if (sources.sources().stream().noneMatch(source -> source.id().equals(key.sourceId()))) {
            throw new IllegalArgumentException("Unknown sourceId: " + key.sourceId());
        }
    }

    private @Nullable SelectedIcon currentIcon(ContainerPreferencesKey key) {
        @Nullable ContainerPreferences current = preferences.snapshot().get(key);
        return current == null ? null : current.icon();
    }

    private void deleteUpload(@Nullable SelectedIcon icon) {
        if (icon != null && icon.selectsUpload()) {
            deleteQuietly(icon.name());
        }
    }

    private void deleteQuietly(String name) {
        try {
            files.delete(name);
        } catch (RuntimeException e) {
            log.warn("Could not delete uploaded icon {}", name, e);
        }
    }
}
