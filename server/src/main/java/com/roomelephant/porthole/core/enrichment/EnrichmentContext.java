package com.roomelephant.porthole.core.enrichment;

import org.jspecify.annotations.*;

@NullMarked
public record EnrichmentContext(
        String sourceId,
        String host,
        @Nullable String publishedHost,
        boolean includeWithoutEndpoints,
        boolean includeStopped) {}
