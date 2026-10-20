package com.roomelephant.porthole.inbound.web;

import com.roomelephant.porthole.core.model.ContainerQuery;
import com.roomelephant.porthole.core.port.inbound.GetContainers;
import com.roomelephant.porthole.inbound.web.mapper.ContainerDTOMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
@RequestMapping("/api")
public class ContainerController {

    private final GetContainers containerService;
    private final ContainerDTOMapper containerDTOMapper;

    public ContainerController(GetContainers containerService, ContainerDTOMapper containerDTOMapper) {
        this.containerService = containerService;
        this.containerDTOMapper = containerDTOMapper;
    }

    @GetMapping("/containers")
    public com.roomelephant.porthole.inbound.web.dto.ContainerInventoryDTO getContainers(
            @RequestParam(defaultValue = "false") boolean includeWithoutEndpoints,
            @RequestParam(defaultValue = "false") boolean includeStopped,
            HttpServletRequest request) {
        return containerDTOMapper.toDTO(containerService.getContainers(
                new ContainerQuery(includeWithoutEndpoints, includeStopped, request.getServerName())));
    }
}
