package com.roomelephant.porthole.controller;

import com.roomelephant.porthole.domain.model.ContainerDTO;
import com.roomelephant.porthole.domain.model.VersionDTO;
import com.roomelephant.porthole.domain.service.ContainerService;
import com.roomelephant.porthole.domain.service.VersionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ContainerController {

    private final ContainerService containerService;
    private final VersionService versionService;

    public ContainerController(ContainerService containerService, VersionService versionService) {
        this.containerService = containerService;
        this.versionService = versionService;
    }

    @GetMapping("/containers")
    public List<ContainerDTO> getContainers(
            @RequestParam(defaultValue = "false") boolean includeWithoutEndpoints,
            @RequestParam(defaultValue = "false") boolean includeStopped,
            HttpServletRequest request) {
        return containerService.getContainers(includeWithoutEndpoints, includeStopped, request.getServerName());
    }

    @GetMapping("/containers/{containerId}/version")
    public VersionDTO getVersion(@PathVariable String containerId) {
        return versionService.getVersionInfo(containerId);
    }
}
