package com.roomelephant.porthole.core.enrichment.endpoint;

import org.jspecify.annotations.*;

@NullMarked
public record EndpointSettings(@Nullable String publishedHost) {}
