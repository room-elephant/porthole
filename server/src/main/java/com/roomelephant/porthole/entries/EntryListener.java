package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Told after each saved change to the entries. Called on the writing thread; must not block for long. */
@NullMarked
public interface EntryListener {
    /**
     * @param saved the entry as saved, or null when it was deleted
     */
    void onEntriesChanged(EntryRef ref, @Nullable EntryRecord saved);
}
