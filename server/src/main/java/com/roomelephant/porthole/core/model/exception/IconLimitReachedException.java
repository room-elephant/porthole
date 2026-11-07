package com.roomelephant.porthole.core.model.exception;

public class IconLimitReachedException extends RuntimeException {
    public IconLimitReachedException(int limit) {
        super("Uploaded icon limit of " + limit + " reached");
    }
}
