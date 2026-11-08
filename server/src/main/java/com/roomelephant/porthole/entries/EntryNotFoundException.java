package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;

/** No entry has this ID, or the ID names a source that isn't configured. */
@NullMarked
public class EntryNotFoundException extends RuntimeException {
    public EntryNotFoundException(String id) {
        super("No entry " + id);
    }
}
