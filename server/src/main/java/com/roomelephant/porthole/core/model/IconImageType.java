package com.roomelephant.porthole.core.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/** Image formats accepted for uploaded icons, recognized from their content rather than their name. */
@NullMarked
public enum IconImageType {
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
    JPEG("jpg", "image/jpeg");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_SIGNATURE = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_SIGNATURE = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final int WEBP_SIGNATURE_OFFSET = 8;

    private final String extension;
    private final String contentType;

    IconImageType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<IconImageType> detect(byte[] bytes) {
        if (startsWith(bytes, PNG_SIGNATURE, 0)) {
            return Optional.of(PNG);
        }
        if (startsWith(bytes, JPEG_SIGNATURE, 0)) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, RIFF_SIGNATURE, 0) && startsWith(bytes, WEBP_SIGNATURE, WEBP_SIGNATURE_OFFSET)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public static Optional<IconImageType> fromFileName(String name) {
        return Arrays.stream(values())
                .filter(type -> name.endsWith("." + type.extension))
                .findFirst();
    }

    private static boolean startsWith(byte[] bytes, byte[] signature, int offset) {
        return bytes.length >= offset + signature.length
                && Arrays.equals(bytes, offset, offset + signature.length, signature, 0, signature.length);
    }
}
