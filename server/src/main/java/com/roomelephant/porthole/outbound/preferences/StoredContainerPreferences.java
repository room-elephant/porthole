package com.roomelephant.porthole.outbound.preferences;

import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.node.ObjectNode;

@NullMarked
public record StoredContainerPreferences(String sourceId, String containerName, ObjectNode preferences) {}
