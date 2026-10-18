package com.roomelephant.porthole.outbound.docker;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.roomelephant.porthole.core.model.ContainerSnapshot;
import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DockerContainerMapper {
    public ContainerSnapshot toSnapshot(Container raw) {
        var names = raw.getNames();
        String name = names == null || names.length == 0 ? "Unknown" : names[0].substring(1);
        var ports = raw.getPorts();
        Set<Integer> published = ports == null
                ? Set.of()
                : Arrays.stream(ports)
                        .map(ContainerPort::getPublicPort)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        return new ContainerSnapshot(
                raw.getId(),
                name,
                raw.getImage(),
                published,
                raw.getLabels() == null ? Map.of() : raw.getLabels(),
                raw.getState(),
                raw.getStatus());
    }
}
