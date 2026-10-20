package com.roomelephant.porthole.inbound.web.dto;

import com.roomelephant.porthole.core.model.SourceStatus;
import org.jspecify.annotations.*;

@NullMarked
public record SourceDTO(
        String id,
        String name,
        SourceStatus status,
        @Nullable String error) {}
