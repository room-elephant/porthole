package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A link as the dashboard shows it, with its saved icon resolved to a URL. */
@NullMarked
public record PresentedLink(
        String id,
        String name,
        String url,
        @Nullable String stack,
        @Nullable String iconUrl) {}
