package com.roomelephant.porthole.fleet;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Receives a source's container events. Events only say that something changed; they carry no state. */
@NullMarked
public interface ContainerEventListener {
    void onEvent();

    /** The stream ended, with the failure that ended it or {@code null} when it completed. */
    void onClosed(@Nullable Throwable failure);
}
