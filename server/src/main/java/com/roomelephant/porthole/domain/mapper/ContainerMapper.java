package com.roomelephant.porthole.domain.mapper;

import static com.roomelephant.porthole.domain.util.ImageUtils.UNKNOWN_IMAGE_NAME;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.roomelephant.porthole.domain.component.IconComponent;
import com.roomelephant.porthole.domain.component.endpoint.EndpointDiscovery;
import com.roomelephant.porthole.domain.model.ContainerDTO;
import com.roomelephant.porthole.domain.model.ContainerSnapshot;
import com.roomelephant.porthole.domain.model.EndpointContext;
import com.roomelephant.porthole.domain.util.ImageUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerMapper {

    private static final String LABEL_PROJECT = "com.docker.compose.project";

    private final IconComponent iconComponent;

    private final EndpointDiscovery endpointDiscovery;

    public ContainerMapper(IconComponent iconComponent, EndpointDiscovery endpointDiscovery) {
        this.endpointDiscovery = endpointDiscovery;
        this.iconComponent = iconComponent;
    }

    public @NonNull ContainerDTO toDTO(@NonNull Container container, EndpointContext context) {
        String name = getName(container.getNames());
        String imageFull = container.getImage();
        Set<Integer> ports = getPorts(container.getPorts());
        var labels = container.getLabels() == null ? Map.<String, String>of() : Map.copyOf(container.getLabels());
        String project = getProject(labels);
        String state = container.getState();
        String status = container.getStatus();

        String displayName = computeDisplayName(name, project);
        String iconUrl = resolveIconUrl(imageFull);

        return new ContainerDTO(
                container.getId(),
                name,
                displayName,
                imageFull,
                endpointDiscovery.discover(
                        new ContainerSnapshot(container.getId(), name, imageFull, ports, labels, state, status),
                        context),
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
            return name.substring(project.length() + 1);
        }
        return name;
    }

    private @NonNull String resolveIconUrl(String imageFull) {
        return iconComponent.resolveIcon(ImageUtils.extractName(imageFull));
    }
}
