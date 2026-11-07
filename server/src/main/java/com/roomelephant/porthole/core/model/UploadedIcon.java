package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record UploadedIcon(byte[] bytes, String contentType) {}
