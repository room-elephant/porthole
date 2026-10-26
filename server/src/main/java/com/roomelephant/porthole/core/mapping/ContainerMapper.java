package com.roomelephant.porthole.core.mapping;

import com.roomelephant.porthole.core.model.*;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerMapper {
    public List<Container> toModels(List<ContainerSnapshot> snapshots, String sourceId) {
        return snapshots.stream()
                .filter(c -> c.id() != null)
                .map(c -> toModel(c, sourceId))
                .toList();
    }

    public Container toModel(ContainerSnapshot c, String sourceId) {
        String image = c.image();
        if (image == null) {
            image = ImageUtils.UNKNOWN_IMAGE_NAME;
        }
        return new Container(
                Objects.requireNonNull(c.id()),
                sourceId,
                c.name(),
                c.name(),
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
