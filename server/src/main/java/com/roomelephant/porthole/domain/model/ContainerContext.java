package com.roomelephant.porthole.domain.model;

import org.jspecify.annotations.*;

@NullMarked
public record ContainerContext(
        String sourceId,
        String host,
        @Nullable String publishedHost,
        boolean includeWithoutEndpoints,
        boolean includeStopped) {}
