package com.roomelephant.porthole.controller;

import com.roomelephant.porthole.controller.mapper.ContainerDTOMapper;
import com.roomelephant.porthole.domain.service.ContainerService;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ContainerController {

    private final ContainerService containerService;
    private final ContainerDTOMapper containerDTOMapper;

    public ContainerController(ContainerService containerService, ContainerDTOMapper containerDTOMapper) {
        this.containerService = containerService;
        this.containerDTOMapper = containerDTOMapper;
    }

    @GetMapping("/containers")
    public com.roomelephant.porthole.controller.model.ContainerInventoryDTO getContainers(
            @RequestParam(defaultValue = "false") boolean includeWithoutEndpoints,
            @RequestParam(defaultValue = "false") boolean includeStopped,
            HttpServletRequest request) {
        return containerDTOMapper.toDTO(
                containerService.getContainers(includeWithoutEndpoints, includeStopped, request.getServerName()));
    }
}
