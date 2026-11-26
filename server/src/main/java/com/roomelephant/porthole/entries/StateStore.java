package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;

/** Durable storage for every saved entry, read once at startup and replaced whole on each change. */
@NullMarked
public interface StateStore {
    /** @throws IllegalStateException when stored state exists but can't be read or is invalid */
    EntryState load();

    /** @throws IllegalStateException when the state could not be saved; the stored state is then unchanged */
    void save(EntryState state);
}
