package com.roomelephant.porthole.core.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding(VersionInfo.class)
public record VersionInfo(
        @Nullable String currentVersion, @Nullable String latestVersion, boolean updateAvailable) {}
