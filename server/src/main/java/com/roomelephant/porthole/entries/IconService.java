package com.roomelephant.porthole.entries;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

/**
 * Uploaded icon files. An icon's ID is the SHA-256 of its stored bytes, so uploading the same image twice stores it
 * once. Files no entry uses are removed by {@link #sweep} once they are old enough that no client is still about to
 * save them.
 */
@NullMarked
@Service
public class IconService {
    public static final int MAX_FILES = 500;
    public static final int MAX_BYTES = 1024 * 1024;

    private final IconFileStore files;

    public IconService(IconFileStore files) {
        this.files = files;
    }

    /**
     * @return the uploaded icon's ID
     * @throws IconTooLargeException over {@link #MAX_BYTES}
     * @throws IllegalArgumentException when the bytes are not a PNG, WebP, JPEG or SVG image
     * @throws IconLimitReachedException when {@link #MAX_FILES} other files are stored
     */
    public synchronized String upload(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new IconTooLargeException();
        Optional<IconImageType> raster = IconImageType.detect(bytes);
        IconImageType type = raster.orElse(IconImageType.SVG);
        byte[] content = raster.isPresent()
                ? bytes
                : SvgSanitizer.sanitize(bytes)
                        .orElseThrow(() -> new IllegalArgumentException("Icon must be a PNG, WebP, JPEG or SVG image"));
        String id = sha256(content) + "." + type.extension();
        if (files.touch(id)) return id;
        if (files.count() >= MAX_FILES) throw new IconLimitReachedException(MAX_FILES);
        files.store(id, content);
        return id;
    }

    public Optional<StoredIcon> read(String id) {
        if (!Icon.UPLOAD_ID.matcher(id).matches()) return Optional.empty();
        return IconImageType.fromFileName(id)
                .flatMap(type -> files.read(id).map(bytes -> new StoredIcon(bytes, type.contentType())));
    }

    /** Marks an upload as about to be referenced, so a sweep can't remove it first. @return whether it exists */
    public synchronized boolean claim(String id) {
        return Icon.UPLOAD_ID.matcher(id).matches() && files.touch(id);
    }

    /** Deletes uploads not in {@code referenced} that nothing has used for at least {@code minAge}. */
    public synchronized int sweep(Set<String> referenced, Instant now, Duration minAge) {
        return files.deleteUnreferenced(referenced, now.minus(minAge));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
