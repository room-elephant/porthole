package com.roomelephant.porthole.fleet;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourceSnapshotTest {
    private static final Source SOURCE = new Source("walker", "Walker", null, true, Duration.ofSeconds(5));
    private static final Instant T = Instant.parse("2026-11-08T10:00:00Z");
    private static final Duration RETENTION = Duration.ofHours(24);

    private static ContainerSnapshot container(String imageId) {
        return new ContainerSnapshot("id", "app", "nginx:1", imageId, Set.of(), Map.of(), "running", "Up");
    }

    @Test
    void initialIsDownWaitingWithoutContainers() {
        SourceSnapshot initial = SourceSnapshot.initial(SOURCE);
        assertThat(initial.source()).isEqualTo(SOURCE);
        assertThat(initial.status()).isEqualTo(SourceSnapshot.Status.DOWN);
        assertThat(initial.error()).isEqualTo("Waiting for first check");
        assertThat(initial.lastSuccessAt()).isNull();
        assertThat(initial.containers()).isEmpty();
    }

    @Test
    void successReplacesContainersAndStampsTime() {
        SourceSnapshot up = SourceSnapshot.initial(SOURCE).succeeded(List.of(container("a")), T);
        assertThat(up.status()).isEqualTo(SourceSnapshot.Status.UP);
        assertThat(up.error()).isNull();
        assertThat(up.lastSuccessAt()).isEqualTo(T);
        assertThat(up.containers()).containsExactly(container("a"));
    }

    @Test
    void failureKeepsContainersWithinRetention() {
        SourceSnapshot down = SourceSnapshot.initial(SOURCE)
                .succeeded(List.of(container("a")), T)
                .failed(SourceSnapshot.Status.DOWN, "Docker is not reachable", T.plus(RETENTION), RETENTION);
        assertThat(down.status()).isEqualTo(SourceSnapshot.Status.DOWN);
        assertThat(down.error()).isEqualTo("Docker is not reachable");
        assertThat(down.lastSuccessAt()).isEqualTo(T);
        assertThat(down.containers()).containsExactly(container("a"));
    }

    @Test
    void failureDropsContainersAfterRetention() {
        SourceSnapshot expired = SourceSnapshot.initial(SOURCE)
                .succeeded(List.of(container("a")), T)
                .failed(
                        SourceSnapshot.Status.ERROR,
                        "Could not retrieve containers",
                        T.plus(RETENTION).plusSeconds(1),
                        RETENTION);
        assertThat(expired.status()).isEqualTo(SourceSnapshot.Status.ERROR);
        assertThat(expired.containers()).isEmpty();
        assertThat(expired.lastSuccessAt()).isEqualTo(T);
    }

    @Test
    void failureBeforeAnySuccessHasNoContainers() {
        SourceSnapshot down = SourceSnapshot.initial(SOURCE)
                .failed(SourceSnapshot.Status.DOWN, "Docker request timed out", T, RETENTION);
        assertThat(down.error()).isEqualTo("Docker request timed out");
        assertThat(down.lastSuccessAt()).isNull();
        assertThat(down.containers()).isEmpty();
    }

    @Test
    void sameContentIgnoresLastSuccessAt() {
        SourceSnapshot first = SourceSnapshot.initial(SOURCE).succeeded(List.of(container("a")), T);
        SourceSnapshot second = first.succeeded(List.of(container("a")), T.plusSeconds(30));
        assertThat(first.sameContent(second)).isTrue();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void sameContentDetectsImageIdChange() {
        SourceSnapshot first = SourceSnapshot.initial(SOURCE).succeeded(List.of(container("a")), T);
        assertThat(first.sameContent(first.succeeded(List.of(container("b")), T)))
                .isFalse();
        assertThat(first.sameContent(first.failed(SourceSnapshot.Status.DOWN, "Docker is not reachable", T, RETENTION)))
                .isFalse();
    }
}
