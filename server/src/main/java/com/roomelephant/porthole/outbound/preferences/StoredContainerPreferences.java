package com.roomelephant.porthole.outbound.preferences;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import org.jspecify.annotations.NullMarked;

/** One record of the preferences file; Jackson may leave any field null when reading a malformed file. */
@NullMarked
public record StoredContainerPreferences(String sourceId, String containerName, ContainerPreferences preferences) {}
