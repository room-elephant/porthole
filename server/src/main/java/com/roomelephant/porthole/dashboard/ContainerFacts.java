package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.versions.VersionInfo;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * @param name the Docker container name
 * @param defaultName the name shown when the user saved none, after Compose prefixes and collisions
 */
@NullMarked
public record ContainerFacts(
        String sourceId,
        String name,
        String defaultName,
        String image,
        @Nullable String state,
        @Nullable String status,
        boolean self,
        @Nullable VersionInfo version) {}
