package com.roomelephant.porthole.entries.web;

import com.roomelephant.porthole.entries.IconService;
import com.roomelephant.porthole.entries.IconTooLargeException;
import com.roomelephant.porthole.entries.StoredIcon;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
@RequestMapping("/api/icons")
@Tag(name = "Icons", description = "Uploaded icon images, shared by content.")
public class IconController {
    private static final Duration CACHE_LIFETIME = Duration.ofDays(365);
    private static final String SVG_MEDIA_TYPE = "image/svg+xml";

    /** Stops an SVG opened directly as a page from running scripts or loading anything, as a second line of defense. */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; style-src 'unsafe-inline'; img-src data:; sandbox";

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

    @GetMapping(
            path = "/{id}",
            produces = {MediaType.IMAGE_PNG_VALUE, "image/webp", MediaType.IMAGE_JPEG_VALUE, SVG_MEDIA_TYPE})
    @Operation(
            operationId = "getIcon",
            summary = "Get an uploaded icon",
            description = "An icon's ID is the hash of its content, so a response never changes.")
    @ApiResponse(responseCode = "200", description = "The uploaded image")
    @ApiResponse(responseCode = "404", description = "Unknown or invalid icon ID")
    public ResponseEntity<byte[]> get(@PathVariable String id) {
        return icons.read(id)
                .map(IconController::imageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> imageResponse(StoredIcon icon) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(icon.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .cacheControl(CacheControl.maxAge(CACHE_LIFETIME).cachePublic().immutable())
                .body(icon.bytes());
    }
}
