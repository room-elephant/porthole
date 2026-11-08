package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ExternalLink;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.model.UploadedIcon;
import com.roomelephant.porthole.core.model.exception.IconLimitReachedException;
import com.roomelephant.porthole.core.port.inbound.ReadUploadedIcon;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import com.roomelephant.porthole.core.port.outbound.ExternalLinkStore;
import com.roomelephant.porthole.core.port.outbound.IconFileStore;
import com.roomelephant.porthole.entries.IconImageType;
import com.roomelephant.porthole.entries.SvgSanitizer;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The uploaded icon files that containers and links share. One lock covers each save, so the upload limit holds
 * across both. Callers say how to read and save their own icon choice.
 */
@NullMarked
@Service
public class UploadedIcons implements ReadUploadedIcon {
    static final int MAX_UPLOADS = 500;

    private static final Logger log = LoggerFactory.getLogger(UploadedIcons.class);

    private final IconFileStore files;
    private final ContainerPreferencesStore preferences;
    private final ExternalLinkStore links;

    public UploadedIcons(IconFileStore files, ContainerPreferencesStore preferences, ExternalLinkStore links) {
        this.files = files;
        this.preferences = preferences;
        this.links = links;
    }

    synchronized void saveLibrary(
            SelectedIcon icon, Supplier<@Nullable SelectedIcon> current, Consumer<SelectedIcon> persist) {
        @Nullable SelectedIcon previous = current.get();
        persist.accept(icon);
        deleteQuietly(previous);
    }

    synchronized void saveUpload(
            byte[] bytes, Supplier<@Nullable SelectedIcon> current, Consumer<SelectedIcon> persist) {
        Optional<IconImageType> raster = IconImageType.detect(bytes);
        IconImageType type = raster.orElse(IconImageType.SVG);
        byte[] content = raster.isPresent()
                ? bytes
                : SvgSanitizer.sanitize(bytes)
                        .orElseThrow(() -> new IllegalArgumentException("Icon must be a PNG, WebP, JPEG or SVG image"));
        @Nullable SelectedIcon previous = current.get();
        int replaced = previous != null && previous.selectsUpload() ? 1 : 0;
        if (files.count() - replaced >= MAX_UPLOADS) {
            throw new IconLimitReachedException(MAX_UPLOADS);
        }
        String name = files.store(content, type);
        try {
            persist.accept(SelectedIcon.upload(name));
        } catch (RuntimeException e) {
            deleteFileQuietly(name);
            throw e;
        }
        deleteQuietly(previous);
    }

    /** Removes the file behind an icon that is no longer used. A failure is only logged. */
    synchronized void deleteQuietly(@Nullable SelectedIcon icon) {
        if (icon != null && icon.selectsUpload()) {
            deleteFileQuietly(icon.name());
        }
    }

    @Override
    public Optional<UploadedIcon> read(String name) {
        if (!SelectedIcon.UPLOAD_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        return IconImageType.fromFileName(name)
                .flatMap(type -> files.read(name).map(bytes -> new UploadedIcon(bytes, type.contentType())));
    }

    /** Best-effort removal of uploads nothing references, which a crash between storing and saving can leave. */
    public synchronized void removeUnreferenced() {
        Set<String> referenced = Stream.concat(
                        preferences.snapshot().values().stream().map(ContainerPreferences::icon),
                        links.snapshot().stream().map(ExternalLink::icon))
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

    private void deleteFileQuietly(String name) {
        try {
            files.delete(name);
        } catch (RuntimeException e) {
            log.warn("Could not delete uploaded icon {}", name, e);
        }
    }
}
