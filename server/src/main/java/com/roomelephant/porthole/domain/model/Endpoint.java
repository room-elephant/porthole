package com.roomelephant.porthole.domain.model;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record Endpoint(String id, String source, String url) {}
