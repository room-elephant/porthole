package com.roomelephant.porthole.domain.model;

import java.util.List;

public record ContainerInventory(List<SourceState> sources, List<Container> containers) {
    public ContainerInventory {
        sources = List.copyOf(sources);
        containers = List.copyOf(containers);
    }
}
