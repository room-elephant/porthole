package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.fleet.ContainerSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Short names for a source's containers: the Compose project prefix and replica suffix are dropped unless that makes
 * two containers of one project look the same.
 */
@NullMarked
public final class DisplayNames {
    static final String COMPOSE_PROJECT = "com.docker.compose.project";
    private static final Pattern REPLICA_SUFFIX = Pattern.compile("-\\d+$");

    /** @return one name per container, in order; collisions are judged across the whole list */
    public List<String> resolve(List<ContainerSnapshot> containers) {
        Set<Key> collisions = new HashSet<>();
        boolean changed;
        do {
            Map<Key, Long> counts = containers.stream()
                    .map(c -> new Key(project(c), resolve(c.name(), project(c), collisions)))
                    .collect(Collectors.groupingBy(key -> key, Collectors.counting()));
            changed = collisions.addAll(counts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet()));
        } while (changed);
        return containers.stream()
                .map(c -> resolve(c.name(), project(c), collisions))
                .toList();
    }

    static @Nullable String project(ContainerSnapshot container) {
        return container.labels().get(COMPOSE_PROJECT);
    }

    private static String shortName(String name, @Nullable String project) {
        if (project != null && name.startsWith(project + "-")) {
            String withoutProject = stripProject(name, project);
            String withoutReplica = REPLICA_SUFFIX.matcher(withoutProject).replaceFirst("");
            return withoutReplica.isEmpty() ? withoutProject : withoutReplica;
        }
        return name;
    }

    private static String resolve(String name, @Nullable String project, Set<Key> collisions) {
        String shortName = shortName(name, project);
        return collisions.contains(new Key(project, shortName)) ? stripProject(name, project) : shortName;
    }

    private static String stripProject(String name, @Nullable String project) {
        return project != null && name.startsWith(project + "-") ? name.substring(project.length() + 1) : name;
    }

    private record Key(@Nullable String project, String name) {}
}
