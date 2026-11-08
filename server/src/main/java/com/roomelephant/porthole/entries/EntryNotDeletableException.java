package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;

/** Container entries come from Docker, so only links can be deleted. */
@NullMarked
public class EntryNotDeletableException extends RuntimeException {
    public EntryNotDeletableException() {
        super("Container entries can't be deleted");
    }
}
