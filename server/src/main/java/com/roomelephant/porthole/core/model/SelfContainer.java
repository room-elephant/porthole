package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The ID of the container Porthole itself runs in, if known. A full 64-character ID matches by equality on any source;
 * a shorter ID (e.g. a hostname) matches as a prefix only on local sources.
 */
@NullMarked
public record SelfContainer(@Nullable String id) {
    private static final int FULL_ID_LENGTH = 64;

    public static SelfContainer none() {
        return new SelfContainer(null);
    }

    public boolean matches(String containerId, boolean localSource) {
        if (id == null) return false;
        if (id.length() == FULL_ID_LENGTH) return containerId.equals(id);
        return localSource && containerId.startsWith(id);
    }
}
