package com.roomelephant.porthole.inbound.web;

import com.roomelephant.porthole.core.port.inbound.ListLinks;
import com.roomelephant.porthole.inbound.web.dto.LinkDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
@RequestMapping("/api/links")
@Tag(name = "Links", description = "Pages the user added to the dashboard, separate from Docker containers.")
public class LinkController {
    private final ListLinks links;

    public LinkController(ListLinks links) {
        this.links = links;
    }

    @GetMapping
    @Operation(
            operationId = "listLinks",
            summary = "List links",
            description = "Links are entries: add, change and delete them through /api/entries.")
    @ApiResponse(responseCode = "200", description = "All saved links")
    public List<LinkDTO> list() {
        return links.list().stream().map(LinkDTO::from).toList();
    }
}
