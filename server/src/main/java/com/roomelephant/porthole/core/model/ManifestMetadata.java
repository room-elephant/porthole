package com.roomelephant.porthole.core.model;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ManifestMetadata(String digest, Kind kind) {
    public enum Kind {
        IMAGE,
        INDEX,
        UNKNOWN
    }

    public ManifestMetadata {
        if (ImageReference.tryParse("image@" + digest).isEmpty())
            throw new IllegalArgumentException("Invalid manifest digest");
        Objects.requireNonNull(kind);
    }
}
