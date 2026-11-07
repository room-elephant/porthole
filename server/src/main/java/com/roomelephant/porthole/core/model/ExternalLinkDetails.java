package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** What a user sets on a link. Values from a request are unchecked until {@link ExternalLinkValidation#normalize}. */
@NullMarked
public record ExternalLinkDetails(
        @Nullable String name,
        @Nullable String url,
        @Nullable String stack) {}
