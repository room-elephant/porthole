package com.roomelephant.porthole.fleet.web;

import com.roomelephant.porthole.fleet.SourceSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding(SourceResponse.class)
@Schema(description = "A configured Docker source and what Porthole last knew about it")
public record SourceResponse(
        @Schema(description = "Configured source ID", example = "walker")
        String id,

        @Schema(description = "Configured display name", example = "Walker")
        String name,

        @Schema(
                description = "UP when the latest check succeeded, DOWN when Docker could not be reached in time, "
                        + "ERROR for other failures")
        SourceSnapshot.Status status,

        @Schema(description = "Why the source is not UP", nullable = true) @Nullable
        String error,

        @Schema(description = "When the source last answered, or null if it never has", nullable = true) @Nullable
        Instant lastSuccessAt) {
    static SourceResponse from(SourceSnapshot snapshot) {
        return new SourceResponse(
                snapshot.source().id(),
                snapshot.source().name(),
                snapshot.status(),
                snapshot.error(),
                snapshot.lastSuccessAt());
    }
}
