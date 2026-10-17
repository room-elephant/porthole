package com.roomelephant.porthole.domain.model;

import org.jspecify.annotations.*;

@NullMarked
public record SourceState(
        String id,
        String name,
        SourceStatus status,
        @Nullable String error) {}
