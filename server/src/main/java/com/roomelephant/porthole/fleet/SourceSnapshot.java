package com.roomelephant.porthole.fleet;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What Porthole last knew about one source. A failed source keeps its last good containers until the retention
 * period since its last success has passed.
 */
@NullMarked
public record SourceSnapshot(
        Source source,
        Status status,
        @Nullable String error,
        @Nullable Instant lastSuccessAt,
        List<ContainerSnapshot> containers) {
    public static final String WAITING = "Waiting for first check";

    public enum Status {
        UP,
        DOWN,
        ERROR
    }

    public SourceSnapshot {
        containers = List.copyOf(containers);
    }

    public static SourceSnapshot initial(Source source) {
        return new SourceSnapshot(source, Status.DOWN, WAITING, null, List.of());
    }

    public SourceSnapshot succeeded(List<ContainerSnapshot> listed, Instant now) {
        return new SourceSnapshot(source, Status.UP, null, now, listed);
    }

    public SourceSnapshot failed(Status failure, String message, Instant now, Duration retention) {
        boolean retained = lastSuccessAt != null && !now.isAfter(lastSuccessAt.plus(retention));
        return new SourceSnapshot(source, failure, message, lastSuccessAt, retained ? containers : List.of());
    }

    /**
     * Equal apart from {@code lastSuccessAt}, which changes on every successful poll, and the elapsed times in Docker's
     * status text ("Up 3 minutes", "Exited (0) 2 hours ago"), which change on nearly every listing.
     */
    public boolean sameContent(SourceSnapshot other) {
        return source.equals(other.source)
                && status == other.status
                && Objects.equals(error, other.error)
                && containers.stream()
                        .map(ContainerSnapshot::withoutElapsedTime)
                        .toList()
                        .equals(other.containers.stream()
                                .map(ContainerSnapshot::withoutElapsedTime)
                                .toList());
    }
}
