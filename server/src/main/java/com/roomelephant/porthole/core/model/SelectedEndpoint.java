package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record SelectedEndpoint(
        String id, String source, @Nullable String url) {
    public static final String NONE = "none";

    public static final String CUSTOM = "custom";

    public static final int MAX_URL_LENGTH = HttpUrls.MAX_LENGTH;

    public boolean selectsNone() {
        return NONE.equals(id);
    }

    public boolean selectsCustom() {
        return CUSTOM.equals(source);
    }

    public boolean hasValidCustomUrl() {
        return CUSTOM.equals(id) && CUSTOM.equals(source) && HttpUrls.isValid(url);
    }
}
