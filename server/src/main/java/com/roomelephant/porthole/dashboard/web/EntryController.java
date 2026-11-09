package com.roomelephant.porthole.dashboard.web;

import com.roomelephant.porthole.dashboard.DashboardEntry;
import com.roomelephant.porthole.dashboard.DashboardService;
import com.roomelephant.porthole.entries.EntryRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.EntryService;
import com.roomelephant.porthole.entries.Icon;
import com.roomelephant.porthole.entries.LinkDraft;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@NullMarked
@RestController
@RequestMapping("/api/entries")
@Tag(name = "Entries", description = "What the user saved for containers and links.")
public class EntryController {
    static final String MERGE_PATCH_JSON = "application/merge-patch+json";

    private final EntryService entries;
    private final DashboardService dashboard;
    private final IconUrls icons;

    public EntryController(EntryService entries, DashboardService dashboard, IconUrls icons) {
        this.entries = entries;
        this.dashboard = dashboard;
        this.icons = icons;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createLink", summary = "Add a link")
    @ApiResponse(responseCode = "201", description = "Link added; returns the link as the dashboard shows it")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid name, URL, stack or icon, or an icon that was never uploaded",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "507",
            description = "Link limit reached",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse create(@RequestBody CreateLinkRequest request) {
        JsonNode iconNode = request.icon();
        @Nullable Icon icon = iconNode == null || iconNode.isNull() ? null : EntryPatchReader.icon(iconNode);
        LinkRef ref = entries.createLink(new LinkDraft(request.name(), request.url(), request.stack(), icon));
        DashboardEntry created =
                dashboard.entry(ref).orElseThrow(() -> new IllegalStateException("Saved link is missing: " + ref.id()));
        return EntryResponse.from(created, icons);
    }

    @PatchMapping(
            path = "/{id}",
            consumes = {MERGE_PATCH_JSON, MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            operationId = "updateEntry",
            summary = "Change an entry",
            description = "JSON Merge Patch of name, icon, endpoint and stack. A missing field is kept and null "
                    + "removes the override. A link needs a name and a URL, and only links have a stack. "
                    + "endpoint is {\"id\": candidate} or \"none\" for containers, or {\"url\": url} for either. "
                    + "A container is accepted whether or not it is currently running, if its source is configured.",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = MERGE_PATCH_JSON,
                                            schema = @Schema(implementation = EntryPatchSchema.class))))
    @ApiResponse(responseCode = "200", description = "Saved; returns the entry as the dashboard now shows it")
    @ApiResponse(responseCode = "204", description = "Saved for a container no source currently lists")
    @ApiResponse(
            responseCode = "400",
            description = "A field is invalid for this kind of entry",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Unknown link, unconfigured source or malformed ID",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<EntryResponse> update(@PathVariable String id, @RequestBody JsonNode patch) {
        entries.update(id, EntryPatchReader.read(patch));
        return dashboard
                .entry(EntryRef.parse(id))
                .map(entry -> ResponseEntity.ok(EntryResponse.from(entry, icons)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteEntry", summary = "Delete a link")
    @ApiResponse(responseCode = "204", description = "Link deleted")
    @ApiResponse(
            responseCode = "404",
            description = "Unknown link",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "405",
            description = "Containers can't be deleted",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        entries.delete(id);
    }
}
