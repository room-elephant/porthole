package com.roomelephant.porthole.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.fleet.ContainerSnapshot;
import com.roomelephant.porthole.fleet.Source;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SnapshotDiffTest {
    private static final Source LAB = new Source("lab-a", "Lab A", null, true, Duration.ofSeconds(5));
    private static final Instant T = Instant.parse("2026-11-08T10:00:00Z");

    private static ContainerSnapshot container(String name, String state, String status) {
        return new ContainerSnapshot(name + "-id", name, "nginx:1", "sha256:a", Set.of(80), Map.of(), state, status);
    }

    private static SourceSnapshot up(ContainerSnapshot... containers) {
        return SourceSnapshot.initial(LAB).succeeded(List.of(containers), T);
    }

    @Test
    void describesTheFirstListing() {
        assertThat(SnapshotDiff.describe(null, up(container("web", "running", "Up"), container("db", "running", "Up"))))
                .isEqualTo("lab-a UP: first listing, 2 containers");
    }

    @Test
    void describesAFailedFirstListingByItsError() {
        SourceSnapshot down = SourceSnapshot.initial(LAB)
                .failed(SourceSnapshot.Status.DOWN, "Docker is not reachable", T, Duration.ofHours(24));

        assertThat(SnapshotDiff.describe(null, down)).isEqualTo("lab-a DOWN (Docker is not reachable)");
    }

    @Test
    void describesStateAndStatusChanges() {
        SourceSnapshot before =
                up(container("restarting", "running", "Up 1 second"), container("web", "running", "Up"));
        SourceSnapshot after = up(
                container("restarting", "restarting", "Restarting (1) Less than a second ago"),
                container("web", "running", "Up"));

        assertThat(SnapshotDiff.describe(before, after))
                .isEqualTo("lab-a: restarting state running → restarting,"
                        + " status \"Up 1 second\" → \"Restarting (1) Less than a second ago\"");
    }

    @Test
    void describesAddedAndRemovedContainers() {
        assertThat(SnapshotDiff.describe(
                        up(container("old", "running", "Up")), up(container("new", "created", "Created"))))
                .isEqualTo("lab-a: added new; removed old");
    }

    @Test
    void describesImageAndPortChanges() {
        ContainerSnapshot web = container("web", "running", "Up");
        ContainerSnapshot moved = new ContainerSnapshot(
                "web-id", "web", "nginx:2", "sha256:b", Set.of(8080), Map.of("a", "b"), "running", "Up");

        assertThat(SnapshotDiff.describe(up(web), up(moved)))
                .isEqualTo("lab-a: web image nginx:1 → nginx:2, image ID sha256:a → sha256:b,"
                        + " ports [80] → [8080], labels changed");
    }

    @Test
    void describesASourceGoingDownAndRecovering() {
        SourceSnapshot before = up(container("web", "running", "Up"));
        SourceSnapshot down =
                before.failed(SourceSnapshot.Status.DOWN, "Docker is not reachable", T, Duration.ofHours(24));

        assertThat(SnapshotDiff.describe(before, down)).isEqualTo("lab-a: UP → DOWN (Docker is not reachable)");
        assertThat(SnapshotDiff.describe(down, before)).isEqualTo("lab-a: DOWN → UP");
    }

    @Test
    void describesAChangeItCannotName() {
        SourceSnapshot snapshot = up(container("web", "running", "Up"));

        assertThat(SnapshotDiff.describe(snapshot, snapshot)).isEqualTo("lab-a: changed");
    }
}
