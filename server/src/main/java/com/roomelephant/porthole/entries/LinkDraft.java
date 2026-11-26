package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A new link as a client sent it; checked when the link is created. */
@NullMarked
public record LinkDraft(
        @Nullable String name,
        @Nullable String url,
        @Nullable String stack,
        @Nullable Icon icon) {}
