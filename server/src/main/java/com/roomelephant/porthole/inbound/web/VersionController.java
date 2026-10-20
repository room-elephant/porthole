package com.roomelephant.porthole.inbound.web;

import com.roomelephant.porthole.core.port.inbound.RefreshVersions;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/versions")
public class VersionController {
    private final RefreshVersions versions;

    public VersionController(RefreshVersions versions) {
        this.versions = versions;
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void refreshVersions() {
        versions.requestRefresh();
    }
}
