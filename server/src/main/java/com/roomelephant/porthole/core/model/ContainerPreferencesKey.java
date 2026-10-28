package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ContainerPreferencesKey(String sourceId, String containerName) {}
