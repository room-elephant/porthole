package com.roomelephant.porthole.inbound.web;

import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.ContainerQuery;
import com.roomelephant.porthole.core.port.inbound.GetContainers;
import com.roomelephant.porthole.core.port.inbound.SelectContainerEndpoint;
import com.roomelephant.porthole.inbound.web.dto.ContainerInventoryDTO;
import com.roomelephant.porthole.inbound.web.dto.SelectContainerEndpointRequest;
import com.roomelephant.porthole.inbound.web.mapper.ContainerDTOMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
@RequestMapping("/api")
@Tag(name = "Containers", description = "Container inventory and service endpoint discovery across Docker sources.")
public class ContainerController {

    private final GetContainers containerService;
    private final SelectContainerEndpoint selectContainerEndpoint;
    private final ContainerDTOMapper containerDTOMapper;

    public ContainerController(
            GetContainers containerService,
            ContainerDTOMapper containerDTOMapper,
            SelectContainerEndpoint selectContainerEndpoint) {
        this.containerService = containerService;
        this.selectContainerEndpoint = selectContainerEndpoint;
        this.containerDTOMapper = containerDTOMapper;
    }

    @GetMapping("/containers")
    @Operation(
            summary = "List discovered containers",
            description = "Returns the status of every configured Docker source and a combined container inventory. "
                    + "By default, excludes stopped containers and containers without resolved service endpoint candidates. "
                    + "Endpoints include the configured custom URL and selected flags for navigation; hasWebUi determines tile or chip availability. "
                    + "Explicit none keeps available candidates with all selected flags false. "
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
            @Parameter(description = "Include containers without resolved service endpoint candidates.")
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

    @PutMapping("/containers/selected-url")
    @Operation(
            summary = "Replace a container's saved endpoint selection",
            description =
                    "Saves a discovered endpoint identity, a custom HTTP(S) URL, or the explicit none choice. "
                            + "Explicit null restores automatic selection. Does not require the container or endpoint to be currently available.")
    @ApiResponse(responseCode = "204", description = "Selection saved")
    @ApiResponse(
            responseCode = "400",
            description = "Missing or unconfigured identity, missing selectedEndpoint, or an invalid selection",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Failed to persist preferences; the prior selection remains confirmed",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void selectEndpoint(@RequestBody SelectContainerEndpointRequest request) {
        selectContainerEndpoint.select(
                new ContainerPreferencesKey(request.sourceId(), request.containerName()),
                containerDTOMapper.toModel(request.selectedEndpoint()));
    }
}
