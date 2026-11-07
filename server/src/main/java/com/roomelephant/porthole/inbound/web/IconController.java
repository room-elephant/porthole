package com.roomelephant.porthole.inbound.web;

import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.UploadedIcon;
import com.roomelephant.porthole.core.port.inbound.ReadUploadedIcon;
import com.roomelephant.porthole.core.port.inbound.SelectContainerIcon;
import com.roomelephant.porthole.inbound.web.dto.SelectContainerIconRequest;
import com.roomelephant.porthole.inbound.web.dto.UploadContainerIconForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@NullMarked
@RestController
@RequestMapping("/api")
@Tag(name = "Icons", description = "Custom container icons from the Dashboard Icons library or user uploads.")
public class IconController {
    private static final Duration UPLOAD_CACHE_LIFETIME = Duration.ofDays(365);

    private final SelectContainerIcon selectContainerIcon;
    private final ReadUploadedIcon readUploadedIcon;

    public IconController(SelectContainerIcon selectContainerIcon, ReadUploadedIcon readUploadedIcon) {
        this.selectContainerIcon = selectContainerIcon;
        this.readUploadedIcon = readUploadedIcon;
    }

    @PutMapping("/containers/icon")
    @Operation(
            summary = "Use a library icon for a container",
            description = "Saves a Dashboard Icons library icon name. The name is not checked against the library. "
                    + "Does not require the container to be currently available.")
    @ApiResponse(responseCode = "204", description = "Icon saved")
    @ApiResponse(
            responseCode = "400",
            description = "Missing or unconfigured identity, or an invalid icon name",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Failed to persist preferences; the prior icon remains in use",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void selectIcon(@RequestBody SelectContainerIconRequest request) {
        selectContainerIcon.selectLibraryIcon(
                new ContainerPreferencesKey(request.sourceId(), request.containerName()), request.name());
    }

    @PostMapping(path = "/containers/icon/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload an icon for a container",
            description = "Saves a PNG, WebP or JPEG image of at most 1 MB, identified from the file's content. "
                    + "Replaces any previous icon. Does not require the container to be currently available.",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                            schema = @Schema(implementation = UploadContainerIconForm.class))))
    @ApiResponse(responseCode = "204", description = "Icon saved")
    @ApiResponse(
            responseCode = "400",
            description = "Missing or unconfigured identity, or an unsupported file type",
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
            description = "Failed to save; the prior icon remains in use",
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
    public void uploadIcon(
            @Parameter(hidden = true) @RequestParam String sourceId,
            @Parameter(hidden = true) @RequestParam String containerName,
            @Parameter(hidden = true) @RequestPart MultipartFile file)
            throws IOException {
        selectContainerIcon.uploadIcon(new ContainerPreferencesKey(sourceId, containerName), file.getBytes());
    }

    @GetMapping(
            path = "/icons/{name}",
            produces = {MediaType.IMAGE_PNG_VALUE, "image/webp", MediaType.IMAGE_JPEG_VALUE})
    @Operation(summary = "Get an uploaded icon", description = "Upload names are random, so responses never change.")
    @ApiResponse(responseCode = "200", description = "The uploaded image")
    @ApiResponse(responseCode = "404", description = "Unknown or invalid icon name")
    public ResponseEntity<byte[]> getIcon(@PathVariable String name) {
        return readUploadedIcon
                .read(name)
                .map(IconController::imageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> imageResponse(UploadedIcon icon) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(icon.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(
                        CacheControl.maxAge(UPLOAD_CACHE_LIFETIME).cachePublic().immutable())
                .body(icon.bytes());
    }
}
