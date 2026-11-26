package com.roomelephant.porthole.fleet;

import org.jspecify.annotations.NullMarked;

/** Told about each source snapshot whose content changed. Called on a monitor thread; must not block for long. */
@NullMarked
public interface SnapshotListener {
    void onSnapshot(SourceSnapshot snapshot);
}
