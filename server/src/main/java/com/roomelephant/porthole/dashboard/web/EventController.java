package com.roomelephant.porthole.dashboard.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@NullMarked
@RestController
@Tag(name = "Dashboard", description = "Everything the dashboard shows, in one read.")
public class EventController {
    private final EventStreams streams;

    public EventController(EventStreams streams) {
        this.streams = streams;
    }

    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            operationId = "streamEvents",
            summary = "Stream dashboard changes",
            description = "Server-sent events that signal dashboard changes without carrying them. The stream opens "
                    + "with one `changed` event and sends another, coalesced over 250 ms, whenever the dashboard "
                    + "changes; each has data `{\"revision\":N}`. Refetch GET /api/dashboard when the revision differs "
                    + "from the one you have. A keepalive comment follows every 15 seconds, and `retry` asks clients "
                    + "to reconnect after 5 seconds. At most 100 streams are open at once.")
    @ApiResponse(
            responseCode = "200",
            description = "The event stream",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE))
    @ApiResponse(
            responseCode = "503",
            description = "Too many open streams, or the server is stopping; the response has no body",
            content = @Content)
    public ResponseEntity<SseEmitter> events() {
        return streams.open()
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }
}
