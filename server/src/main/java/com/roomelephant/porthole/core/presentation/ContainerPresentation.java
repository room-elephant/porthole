package com.roomelephant.porthole.core.presentation;

import com.roomelephant.porthole.core.model.*;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerPresentation {
    private final DisplayNameResolver names = new DisplayNameResolver();

    public List<Container> toModels(List<ContainerSnapshot> snapshots, String sourceId) {
        List<ContainerSnapshot> identified =
                snapshots.stream().filter(c -> c.id() != null).toList();
        List<String> displayNames = names.resolve(identified);
        return IntStream.range(0, identified.size())
                .mapToObj(i -> toModel(identified.get(i), sourceId, displayNames.get(i)))
                .toList();
    }

    public Container toModel(ContainerSnapshot snapshot, String sourceId) {
        return toModels(List.of(snapshot), sourceId).getFirst();
    }

    private Container toModel(ContainerSnapshot c, String sourceId, String displayName) {
        String image = c.image();
        if (image == null) {
            image = ImageUtils.UNKNOWN_IMAGE_NAME;
        }
        return new Container(
                Objects.requireNonNull(c.id()),
                sourceId,
                c.name(),
                displayName,
                image,
                new Container.Details(
                        c.publishedPorts(),
                        c.labels(),
                        c.labels().get("com.docker.compose.project"),
                        c.state(),
                        c.status()),
                List.of());
    }
}
