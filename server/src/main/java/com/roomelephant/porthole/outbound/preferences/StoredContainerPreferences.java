package com.roomelephant.porthole.outbound.preferences;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record StoredContainerPreferences(String sourceId, String containerName, ContainerPreferences preferences) {}
