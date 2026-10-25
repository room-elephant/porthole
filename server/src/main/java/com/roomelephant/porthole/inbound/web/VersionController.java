package com.roomelephant.porthole.inbound.web;

import com.roomelephant.porthole.core.port.inbound.RefreshVersions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/versions")
@Tag(name = "Versions", description = "Background image version refreshes.")
public class VersionController {
    private final RefreshVersions versions;

    public VersionController(RefreshVersions versions) {
        this.versions = versions;
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Request an image version refresh",
            description = "Queues an immediate background scan across all configured Docker sources. "
                    + "Returns without waiting for Docker or registry checks to finish. "
                    + "Read GET /api/containers for cached results while the refresh runs and updated results afterward. "
                    + "Repeated requests are coalesced while a source scan is queued or running.")
    @ApiResponse(responseCode = "202", description = "Refresh queued; the response has no body.", content = @Content)
    public void refreshVersions() {
        versions.requestRefresh();
    }
}
