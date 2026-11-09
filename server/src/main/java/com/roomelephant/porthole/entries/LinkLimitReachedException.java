package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class LinkLimitReachedException extends RuntimeException {
    public LinkLimitReachedException(int limit) {
        super("Porthole has reached its limit of " + limit + " links");
    }
}
