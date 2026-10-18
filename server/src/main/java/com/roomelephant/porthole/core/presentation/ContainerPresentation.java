package com.roomelephant.porthole.core.presentation;

import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.presentation.icon.IconResolver;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerPresentation {
    private final IconResolver icons;
    private final DisplayNameResolver names = new DisplayNameResolver();

    public ContainerPresentation(IconResolver icons) {
        this.icons = icons;
    }

    public List<Container> toModels(List<ContainerSnapshot> snapshots, String sourceId) {
        var displayNames = names.resolve(snapshots);
        return IntStream.range(0, snapshots.size())
                .mapToObj(i -> toModel(snapshots.get(i), sourceId, displayNames.get(i)))
                .toList();
    }

    public Container toModel(ContainerSnapshot snapshot, String sourceId) {
        return toModels(List.of(snapshot), sourceId).getFirst();
    }

    private Container toModel(ContainerSnapshot c, String sourceId, String displayName) {
        return new Container(
                c.id(),
                sourceId,
                c.name(),
                displayName,
                c.image(),
                new Container.Details(
                        c.publishedPorts(),
                        c.labels(),
                        icons.resolveIcon(ImageUtils.extractName(c.image())),
                        c.labels().get("com.docker.compose.project"),
                        c.state(),
                        c.status()),
                List.of());
    }
}
