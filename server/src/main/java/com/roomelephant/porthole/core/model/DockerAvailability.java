package com.roomelephant.porthole.core.model;

import java.util.*;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record DockerAvailability(Map<String, Boolean> sources) {
    public DockerAvailability {
        sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
    }

    public boolean anyAvailable() {
        return sources.containsValue(true);
    }
}
