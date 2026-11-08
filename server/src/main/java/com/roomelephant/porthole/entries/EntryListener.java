package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;

/** Told after each saved change to the entries. Called on the writing thread; must not block for long. */
@NullMarked
public interface EntryListener {
    void onEntriesChanged();
}
