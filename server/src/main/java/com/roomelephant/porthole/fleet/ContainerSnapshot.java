package com.roomelephant.porthole.fleet;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.*;

/** Docker metadata as listed, before presentation. */
@NullMarked
public record ContainerSnapshot(
        @Nullable String id,
        String name,
        @Nullable String image,
        @Nullable String imageId,
        Set<Integer> publishedPorts,
        Map<String, String> labels,
        @Nullable String state,
        @Nullable String status) {
    // Docker's human-readable durations: "Less than a second", "About a minute", "About an hour", "41 seconds".
    private static final Pattern ELAPSED = Pattern.compile(
            "Less than a second|About an? (?:minute|hour)|\\d+ (?:second|minute|hour|day|week|month|year)s?");

    public ContainerSnapshot {
        publishedPorts = Set.copyOf(publishedPorts);
        labels = Map.copyOf(labels);
    }

    /** This snapshot with the elapsed times taken out of its status, for telling real changes from the clock. */
    ContainerSnapshot withoutElapsedTime() {
        if (status == null) return this;
        return new ContainerSnapshot(
                id,
                name,
                image,
                imageId,
                publishedPorts,
                labels,
                state,
                ELAPSED.matcher(status).replaceAll(""));
    }
}
