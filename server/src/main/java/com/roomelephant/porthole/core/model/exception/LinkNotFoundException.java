package com.roomelephant.porthole.core.model.exception;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String id) {
        super("Link not found: " + id);
    }
}
