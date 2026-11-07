package com.roomelephant.porthole.inbound.web;

import com.roomelephant.porthole.core.port.inbound.ManageLinks;
import com.roomelephant.porthole.inbound.web.dto.LinkDTO;
import com.roomelephant.porthole.inbound.web.dto.LinkRequest;
import com.roomelephant.porthole.inbound.web.dto.SelectLinkIconRequest;
import com.roomelephant.porthole.inbound.web.dto.UploadLinkIconForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@NullMarked
@RestController
@RequestMapping("/api/links")
@Tag(name = "Links", description = "Pages the user added to the dashboard, separate from Docker containers.")
public class LinkController {
    private final ManageLinks links;

    public LinkController(ManageLinks links) {
        this.links = links;
    }

    @GetMapping
    @Operation(summary = "List links")
    @ApiResponse(responseCode = "200", description = "All saved links")
    public List<LinkDTO> list() {
        return links.list().stream().map(LinkDTO::from).toList();
    }

    @PostMapping
    @Operation(summary = "Add a link")
    @ApiResponse(responseCode = "201", description = "Link added")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid name, URL or stack",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Failed to save",
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
    public LinkDTO create(@RequestBody LinkRequest request) {
        return LinkDTO.from(links.create(request.toDetails()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a link's name, URL and stack", description = "Keeps the link's icon.")
    @ApiResponse(responseCode = "200", description = "Link updated")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid name, URL or stack",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Unknown link",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Failed to save; the prior values remain",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    public LinkDTO update(@PathVariable String id, @RequestBody LinkRequest request) {
        return LinkDTO.from(links.update(id, request.toDetails()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a link", description = "Also deletes its uploaded icon, if it has one.")
    @ApiResponse(responseCode = "204", description = "Link deleted")
    @ApiResponse(
            responseCode = "404",
            description = "Unknown link",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Failed to save; the link remains",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        links.delete(id);
    }

    @PutMapping("/{id}/icon")
    @Operation(
            summary = "Use a library icon for a link",
            description = "Saves a Dashboard Icons library icon name. The name is not checked against the library.")
    @ApiResponse(responseCode = "204", description = "Icon saved")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid icon name",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Unknown link",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Failed to save; the prior icon remains",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void selectIcon(@PathVariable String id, @RequestBody SelectLinkIconRequest request) {
        links.selectLibraryIcon(id, request.name());
    }

    @PostMapping(path = "/{id}/icon/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload an icon for a link",
            description = "Saves a PNG, WebP, JPEG or SVG image of at most 1 MB, identified from the file's content. "
                    + "Scripts and anything else unsafe are removed from an SVG before saving. "
                    + "Replaces any previous icon.",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                            schema = @Schema(implementation = UploadLinkIconForm.class))))
    @ApiResponse(responseCode = "204", description = "Icon saved")
    @ApiResponse(
            responseCode = "400",
            description = "Unsupported file type",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Unknown link",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "413",
            description = "File larger than 1 MB",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Failed to save; the prior icon remains",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "507",
            description = "Uploaded icon limit reached",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadIcon(@PathVariable String id, @Parameter(hidden = true) @RequestPart MultipartFile file)
            throws IOException {
        links.uploadIcon(id, file.getBytes());
    }
}
