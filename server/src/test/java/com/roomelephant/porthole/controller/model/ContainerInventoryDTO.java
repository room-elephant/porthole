package com.roomelephant.porthole.controller.model;

import java.util.List;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@RegisterReflectionForBinding({ContainerInventoryDTO.class, SourceDTO.class})
public record ContainerInventoryDTO(List<SourceDTO> sources, List<ContainerDTO> containers) {
    public ContainerInventoryDTO {
        sources = List.copyOf(sources);
        containers = List.copyOf(containers);
    }
}
