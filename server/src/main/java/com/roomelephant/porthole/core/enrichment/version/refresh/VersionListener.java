package com.roomelephant.porthole.core.enrichment.version.refresh;

import org.jspecify.annotations.NullMarked;

/** Told when a cached version result changes or a manual check starts or completes. Must not block for long. */
@NullMarked
public interface VersionListener {
    void onVersionsChanged(VersionChange change);
}
