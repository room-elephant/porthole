package com.roomelephant.porthole.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.*;

@NullMarked
public record ContainerInspection(
        @Nullable String image, @Nullable String imageId, List<String> environment, Map<String, String> labels) {
    public ContainerInspection {
        environment = Collections.unmodifiableList(new ArrayList<>(environment));
        labels = Collections.unmodifiableMap(new HashMap<>(labels));
    }
}
