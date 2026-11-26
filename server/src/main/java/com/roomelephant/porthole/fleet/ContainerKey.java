package com.roomelephant.porthole.fleet;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ContainerKey(String sourceId, String containerId) {}
