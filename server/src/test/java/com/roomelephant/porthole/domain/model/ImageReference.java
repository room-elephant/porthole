package com.roomelephant.porthole.domain.model;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Compatibility fixture for unchanged black-box ITs; production parsing lives in core. */
public final class ImageReference {
    private ImageReference() {}

    public static Optional<com.roomelephant.porthole.core.model.ImageReference> tryParse(@Nullable String value) {
        return com.roomelephant.porthole.core.model.ImageReference.tryParse(value);
    }
}
