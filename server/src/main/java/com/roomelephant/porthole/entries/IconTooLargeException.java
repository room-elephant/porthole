package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class IconTooLargeException extends RuntimeException {
    public IconTooLargeException() {
        super("Icon must be at most 1 MB");
    }
}
