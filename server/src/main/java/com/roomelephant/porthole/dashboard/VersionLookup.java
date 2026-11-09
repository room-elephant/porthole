package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.fleet.ContainerKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The cached version result for a container, read without doing any work. */
@NullMarked
@FunctionalInterface
public interface VersionLookup {
    @Nullable
    VersionInfo find(ContainerKey key);
}
