package com.roomelephant.porthole.fleet.web;

import com.roomelephant.porthole.fleet.Fleet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
@RequestMapping("/api/sources")
@Tag(name = "Sources", description = "Status of the configured Docker sources.")
public class SourceController {
    private final Fleet fleet;

    public SourceController(Fleet fleet) {
        this.fleet = fleet;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listSources",
            summary = "List Docker sources",
            description = "Returns every configured source in configuration order with its latest status, "
                    + "read from memory without contacting Docker. Always returns 200, even when every source is "
                    + "down. Docker status is not part of the health endpoints.")
    public List<SourceResponse> list() {
        return fleet.snapshots().stream().map(SourceResponse::from).toList();
    }
}
