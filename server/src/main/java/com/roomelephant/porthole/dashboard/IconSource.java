package com.roomelephant.porthole.dashboard;

import org.jspecify.annotations.NullMarked;

/** Where an entry's icon comes from. The web layer turns it into a URL. */
@NullMarked
public sealed interface IconSource permits IconSource.Library, IconSource.Upload, IconSource.Self, IconSource.None {
    record Library(String name) implements IconSource {}

    record Upload(String id) implements IconSource {}

    /** Porthole's own logo, for the container Porthole runs in. */
    record Self() implements IconSource {}

    /** No icon: a link without a saved one. */
    record None() implements IconSource {}
}
