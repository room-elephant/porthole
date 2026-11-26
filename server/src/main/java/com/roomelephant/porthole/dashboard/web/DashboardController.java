package com.roomelephant.porthole.dashboard.web;

import com.roomelephant.porthole.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
@Tag(name = "Dashboard", description = "Everything the dashboard shows, in one read.")
public class DashboardController {
    private final DashboardService dashboard;
    private final IconUrls icons;

    public DashboardController(DashboardService dashboard, IconUrls icons) {
        this.dashboard = dashboard;
        this.icons = icons;
    }

    @GetMapping(path = "/api/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getDashboard",
            summary = "Read the dashboard",
            description = "Every configured source's state and every entry: each container a source lists or still "
                    + "retains after a failure, including stopped ones and ones without endpoints, then each link. "
                    + "Saved names, icons and endpoint choices are applied. Clients filter. Reads only memory, so it "
                    + "never waits on Docker or a registry.")
    @ApiResponse(responseCode = "200", description = "The dashboard, including source failures")
    public DashboardResponse get() {
        return DashboardResponse.from(dashboard.current(), icons);
    }
}
