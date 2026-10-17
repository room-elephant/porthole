package com.roomelephant.porthole.domain.mapper;

import static com.roomelephant.porthole.domain.util.ImageUtils.UNKNOWN_IMAGE_NAME;

import com.github.dockerjava.api.model.ContainerPort;
import com.roomelephant.porthole.domain.component.IconComponent;
import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.util.ImageUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerMapper {

    private static final String LABEL_PROJECT = "com.docker.compose.project";
    private static final Pattern REPLICA_SUFFIX = Pattern.compile("-[0-9]+$");

    private final IconComponent iconComponent;

    public ContainerMapper(IconComponent iconComponent) {
        this.iconComponent = iconComponent;
    }

    public Container toModel(com.github.dockerjava.api.model.Container container, String sourceId) {
        return toModel(container, sourceId, Set.of());
    }

    public List<Container> toModels(List<com.github.dockerjava.api.model.Container> containers, String sourceId) {
        Set<DisplayNameKey> collisions = new HashSet<>();
        boolean changed;
        do {
            Map<DisplayNameKey, Long> counts = containers.stream()
                    .map(container -> {
                        String project = container.getLabels() == null ? null : getProject(container.getLabels());
                        return new DisplayNameKey(
                                project, resolveDisplayName(getName(container.getNames()), project, collisions));
                    })
                    .collect(Collectors.groupingBy(key -> key, Collectors.counting()));
            // Restoring a suffix can collide with another shortened name; repeat until stable.
            changed = collisions.addAll(counts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet()));
        } while (changed);
        return containers.stream()
                .map(container -> toModel(container, sourceId, collisions))
                .toList();
    }

    private Container toModel(
            com.github.dockerjava.api.model.Container container, String sourceId, Set<DisplayNameKey> collisions) {
        String name = getName(container.getNames());
        String imageFull = container.getImage();
        Set<Integer> ports = getPorts(container.getPorts());
        Map<String, String> labels =
                container.getLabels() == null ? Map.<String, String>of() : Map.copyOf(container.getLabels());
        String project = getProject(labels);
        String state = container.getState();
        String status = container.getStatus();

        String displayName = resolveDisplayName(name, project, collisions);
        String iconUrl = resolveIconUrl(imageFull);

        return new Container(
                container.getId(),
                sourceId,
                name,
                displayName,
                imageFull,
                ports,
                labels,
                List.of(),
                iconUrl,
                project,
                state,
                status);
    }

    private @NonNull String getName(String @Nullable [] names) {
        if (names == null) {
            return UNKNOWN_IMAGE_NAME;
        }
        return names.length > 0 ? names[0].substring(1) : UNKNOWN_IMAGE_NAME;
    }

    private @NonNull Set<Integer> getPorts(ContainerPort @Nullable [] ports) {
        if (ports == null) {
            return Collections.emptySet();
        }

        return Arrays.stream(ports)
                .map(ContainerPort::getPublicPort)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private @Nullable String getProject(Map<String, String> labels) {
        return labels.get(LABEL_PROJECT);
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

    private @NonNull String resolveIconUrl(String imageFull) {
        return iconComponent.resolveIcon(ImageUtils.extractName(imageFull));
    }
}
