package com.roomelephant.porthole.domain.model;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ContainerContext(String host, boolean includeWithoutEndpoints, boolean includeStopped) {}
