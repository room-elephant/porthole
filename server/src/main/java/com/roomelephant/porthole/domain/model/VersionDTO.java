package com.roomelephant.porthole.domain.model;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@RegisterReflectionForBinding(VersionDTO.class)
public record VersionDTO(String currentVersion, String latestVersion, boolean updateAvailable) {}
