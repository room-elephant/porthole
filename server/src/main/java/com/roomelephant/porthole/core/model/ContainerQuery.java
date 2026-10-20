package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ContainerQuery(boolean includeWithoutEndpoints, boolean includeStopped, String requestHostname) {}
