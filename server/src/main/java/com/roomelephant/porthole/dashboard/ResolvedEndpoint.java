package com.roomelephant.porthole.dashboard;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ResolvedEndpoint(String id, String source, String url, boolean selected) {}
