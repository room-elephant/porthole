package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record StoredIcon(byte[] bytes, String contentType) {}
