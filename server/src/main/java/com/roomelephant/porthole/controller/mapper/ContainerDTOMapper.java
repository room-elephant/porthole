package com.roomelephant.porthole.controller.mapper;

import com.roomelephant.porthole.controller.model.ContainerDTO;
import com.roomelephant.porthole.controller.model.EndpointDTO;
import com.roomelephant.porthole.domain.model.Container;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerDTOMapper {
    public ContainerDTO toDTO(Container container) {
        return new ContainerDTO(
                container.id(),
                container.sourceId(),
                container.name(),
                container.displayName(),
                container.image(),
                container.urlCandidates().stream()
                        .map(endpoint -> new EndpointDTO(endpoint.id(), endpoint.source(), endpoint.url()))
                        .toList(),
                container.iconUrl(),
                container.project(),
                container.state(),
                container.status(),
                container.version());
    }
}
