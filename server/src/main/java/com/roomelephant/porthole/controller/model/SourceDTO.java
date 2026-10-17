package com.roomelephant.porthole.controller.model;

import com.roomelephant.porthole.domain.model.SourceStatus;
import org.jspecify.annotations.*;

@NullMarked
public record SourceDTO(
        String id,
        String name,
        SourceStatus status,
        @Nullable String error) {}
