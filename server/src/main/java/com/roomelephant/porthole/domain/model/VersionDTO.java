package com.roomelephant.porthole.domain.model;

public record VersionDTO(String currentVersion, String latestVersion, boolean updateAvailable) {}
