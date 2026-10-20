package com.roomelephant.porthole.inbound.web.mapper;

import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.inbound.web.dto.ContainerDTO;
import com.roomelephant.porthole.inbound.web.dto.EndpointDTO;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerDTOMapper {
    public com.roomelephant.porthole.inbound.web.dto.ContainerInventoryDTO toDTO(
            com.roomelephant.porthole.core.model.ContainerInventory inventory) {
        return new com.roomelephant.porthole.inbound.web.dto.ContainerInventoryDTO(
                inventory.sources().stream()
                        .map(s -> new com.roomelephant.porthole.inbound.web.dto.SourceDTO(
                                s.id(), s.name(), s.status(), s.error()))
                        .toList(),
                inventory.containers().stream().map(this::toDTO).toList());
    }

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
                container.version(),
                container.isSelf());
    }
}
