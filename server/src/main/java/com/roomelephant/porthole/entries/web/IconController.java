package com.roomelephant.porthole.entries.web;

import com.roomelephant.porthole.entries.IconService;
import com.roomelephant.porthole.entries.IconTooLargeException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController("entryIconController")
@RequestMapping("/api/icons")
@Tag(name = "Icons", description = "Uploaded icon images, shared by content.")
public class IconController {
    private final IconService icons;

    public IconController(IconService icons) {
        this.icons = icons;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "uploadIcon",
            summary = "Upload an icon",
            description = "Takes the raw image as the body, with any content type. Accepts PNG, WebP, JPEG or SVG "
                    + "of at most 1 MB, identified from the content; scripts and anything else unsafe are removed "
                    + "from an SVG. Returns an ID to save on an entry. The same image always gets the same ID. "
                    + "An upload no entry uses is removed after about an hour.",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                            schema = @Schema(type = "string", format = "binary"))))
    @ApiResponse(responseCode = "201", description = "Icon stored")
    @ApiResponse(
            responseCode = "400",
            description = "Not a PNG, WebP, JPEG or SVG image",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "413",
            description = "Larger than 1 MB",
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
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse upload(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > IconService.MAX_BYTES) throw new IconTooLargeException();
        byte[] bytes;
        try (InputStream body = request.getInputStream()) {
            bytes = body.readNBytes(IconService.MAX_BYTES + 1);
        }
        if (bytes.length > IconService.MAX_BYTES) throw new IconTooLargeException();
        return new CreatedResponse(icons.upload(bytes));
    }
}
