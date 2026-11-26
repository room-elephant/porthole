package com.roomelephant.porthole.versions;

import com.roomelephant.porthole.fleet.ContainerKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** What changed in version tracking. */
@NullMarked
public sealed interface VersionChange {
    /** A container's cached result changed; {@code previous} is null for its first result. */
    record Result(ContainerKey key, @Nullable VersionInfo previous, VersionInfo current) implements VersionChange {}

    /** A manual check started or completed. */
    record Run(VersionChecks checks) implements VersionChange {}
}
