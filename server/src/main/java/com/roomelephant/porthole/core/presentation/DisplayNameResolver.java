package com.roomelephant.porthole.core.presentation;

import com.roomelephant.porthole.core.model.ContainerSnapshot;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.*;

@NullMarked
public final class DisplayNameResolver {
    private static final Pattern REPLICA_SUFFIX = Pattern.compile("-\\d+$");

    public List<String> resolve(List<ContainerSnapshot> containers) {
        Set<DisplayNameKey> collisions = new HashSet<>();
        boolean changed;
        do {
            Map<DisplayNameKey, Long> counts = containers.stream()
                    .map(c -> {
                        String project = c.labels().get("com.docker.compose.project");
                        return new DisplayNameKey(project, resolveDisplayName(c.name(), project, collisions));
                    })
                    .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
            changed = collisions.addAll(counts.entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet()));
        } while (changed);
        return containers.stream()
                .map(c -> resolveDisplayName(c.name(), c.labels().get("com.docker.compose.project"), collisions))
                .toList();
    }

    private @NonNull String computeDisplayName(String name, @Nullable String project) {
        if (project != null && name.startsWith(project + "-")) {
            String displayName = stripProjectPrefix(name, project);
            String shortName = REPLICA_SUFFIX.matcher(displayName).replaceFirst("");
            return shortName.isEmpty() ? displayName : shortName;
        }
        return name;
    }

    private String resolveDisplayName(String name, @Nullable String project, Set<DisplayNameKey> collisions) {
        String shortName = computeDisplayName(name, project);
        return collisions.contains(new DisplayNameKey(project, shortName))
                ? stripProjectPrefix(name, project)
                : shortName;
    }

    private String stripProjectPrefix(String name, @Nullable String project) {
        return project != null && name.startsWith(project + "-") ? name.substring(project.length() + 1) : name;
    }

    private record DisplayNameKey(@Nullable String project, String name) {}
}
