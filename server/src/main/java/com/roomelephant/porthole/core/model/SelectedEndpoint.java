package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record SelectedEndpoint(
        String id, String source, @Nullable String url) {}
