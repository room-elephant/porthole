package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.*;

@NullMarked
public final class ImageUtils {
    public static final String UNKNOWN_IMAGE_NAME = "Unknown";

    private ImageUtils() {}
    /** Extracts the image name without registry, tag, or digest qualifiers. */
    public static String extractName(@Nullable String image) {
        return ImageReference.tryParse(image).map(ImageReference::leafName).orElse(UNKNOWN_IMAGE_NAME);
    }
}
