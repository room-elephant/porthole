package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class IconLimitReachedException extends RuntimeException {
    public IconLimitReachedException(int limit) {
        super("Porthole has reached its limit of " + limit + " uploaded icons");
    }
}
