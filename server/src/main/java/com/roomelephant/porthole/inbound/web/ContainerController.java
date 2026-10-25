package com.roomelephant.porthole.inbound.web;

import com.roomelephant.porthole.core.model.ContainerQuery;
import com.roomelephant.porthole.core.port.inbound.GetContainers;
import com.roomelephant.porthole.inbound.web.dto.ContainerInventoryDTO;
import com.roomelephant.porthole.inbound.web.mapper.ContainerDTOMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
@RequestMapping("/api")
@Tag(name = "Containers", description = "Container inventory and service endpoint discovery across Docker sources.")
public class ContainerController {

    private final GetContainers containerService;
    private final ContainerDTOMapper containerDTOMapper;

    public ContainerController(GetContainers containerService, ContainerDTOMapper containerDTOMapper) {
        this.containerService = containerService;
        this.containerDTOMapper = containerDTOMapper;
    }

    @GetMapping("/containers")
    @Operation(
            summary = "List discovered containers",
            description = "Returns the status of every configured Docker source and a combined container inventory. "
                    + "By default, excludes stopped containers and containers without discovered service endpoints. "
                    + "A failed source is reported as DOWN or ERROR in sources without discarding other sources' containers. "
                    + "Image version information comes from the background refresh cache and may initially be null.")
    @ApiResponse(
            responseCode = "200",
            description = "Container inventory and source statuses, including any source failures.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ContainerInventoryDTO.class)))
    public ContainerInventoryDTO getContainers(
            @Parameter(description = "Include containers without any published-port or Traefik service endpoints.")
                    @RequestParam(defaultValue = "false")
                    boolean includeWithoutEndpoints,
            @Parameter(
                            description =
                                    "Include stopped containers; containers without endpoints still require includeWithoutEndpoints=true.")
                    @RequestParam(defaultValue = "false")
                    boolean includeStopped) {
        return containerDTOMapper.toDTO(
                containerService.getContainers(new ContainerQuery(includeWithoutEndpoints, includeStopped)));
    }
}
