package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ExternalLinkValidation {
    static final int MAX_TEXT_LENGTH = 255;

    private ExternalLinkValidation() {}

    /** Returns the trimmed values, with a blank stack as none. */
    public static ExternalLinkDetails normalize(ExternalLinkDetails details) {
        @Nullable String name = details.name();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String trimmedName = name.trim();
        requireLength(trimmedName, "name");
        @Nullable String url = details.url();
        String trimmedUrl = url == null ? "" : url.trim();
        if (!HttpUrls.isValid(trimmedUrl)) {
            throw new IllegalArgumentException("url must be an absolute HTTP(S) URL without credentials, at most "
                    + HttpUrls.MAX_LENGTH + " characters");
        }
        @Nullable String stack = details.stack();
        String trimmedStack = stack == null ? "" : stack.trim();
        requireLength(trimmedStack, "stack");
        return new ExternalLinkDetails(trimmedName, trimmedUrl, trimmedStack.isEmpty() ? null : trimmedStack);
    }

    /** Checks a link read from storage. */
    public static void validate(ExternalLink link) {
        @Nullable String id = link.id();
        if (id == null || !ExternalLink.ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid link id");
        }
        normalize(new ExternalLinkDetails(link.name(), link.url(), link.stack()));
        @Nullable SelectedIcon icon = link.icon();
        if (icon != null) {
            icon.requireValid();
        }
    }

    private static void requireLength(String value, String field) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + " must be at most " + MAX_TEXT_LENGTH + " characters");
        }
    }
}
