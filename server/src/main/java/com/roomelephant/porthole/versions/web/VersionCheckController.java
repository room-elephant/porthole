package com.roomelephant.porthole.versions.web;

import com.roomelephant.porthole.versions.VersionTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
@Tag(name = "Versions", description = "Image version checks.")
public class VersionCheckController {
    private final VersionTracker versions;

    public VersionCheckController(VersionTracker versions) {
        this.versions = versions;
    }

    @PostMapping("/api/version-checks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            operationId = "startVersionCheck",
            summary = "Start a version check",
            description = "Checks every container on every source for image updates in the background, bypassing "
                    + "cached registry answers. Returns without waiting for Docker or registries. While it runs, "
                    + "GET /api/dashboard reports versionChecks.running, and GET /api/events signals each result and "
                    + "the end of the run. A request while a check is running does nothing.")
    @ApiResponse(responseCode = "202", description = "Check started or already running; no body", content = @Content)
    public void startVersionCheck() {
        versions.startCheck();
    }
}
