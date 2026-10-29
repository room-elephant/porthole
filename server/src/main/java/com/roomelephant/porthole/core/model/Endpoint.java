package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record Endpoint(String id, String source, String url, boolean selected) {}
