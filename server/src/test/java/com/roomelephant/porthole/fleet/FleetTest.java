package com.roomelephant.porthole.fleet;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FleetTest {
    private static final Source B = new Source("b", "B", null, true, Duration.ofSeconds(1));
    private static final Source A = new Source("a", "A", null, false, Duration.ofSeconds(1));
    private static final FleetSettings SETTINGS = new FleetSettings(Duration.ofMillis(20), Duration.ofHours(1));

    private final FakeDocker docker = new FakeDocker();
    private Fleet fleet;

    @AfterEach
    void tearDown() {
        if (fleet != null) fleet.close();
    }

    @Test
    void snapshotsFollowCatalogOrderAndStartInitial() {
        fleet = new Fleet(new SourceCatalog(List.of(B, A)), docker, SETTINGS, List.of());
        assertThat(fleet.snapshots()).containsExactly(SourceSnapshot.initial(B), SourceSnapshot.initial(A));
        assertThat(fleet.snapshot("a")).isEqualTo(SourceSnapshot.initial(A));
    }

    @Test
    void startedFleetPublishesEverySourceToListeners() {
        List<SourceSnapshot> seen = new CopyOnWriteArrayList<>();
        fleet = new Fleet(new SourceCatalog(List.of(B, A)), docker, SETTINGS, List.of(seen::add));
        fleet.start();
        fleet.start();
        await().atMost(Duration.ofSeconds(1))
                .until(() -> fleet.snapshots().stream().allMatch(s -> s.status() == SourceSnapshot.Status.UP));
        await().atMost(Duration.ofSeconds(1)).until(() -> seen.size() == 2);
        assertThat(seen).extracting(s -> s.source().id()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void listenerFailureDoesNotStopOthers() {
        List<SourceSnapshot> seen = new CopyOnWriteArrayList<>();
        SnapshotListener failing = _ -> {
            throw new IllegalStateException("listener broke");
        };
        fleet = new Fleet(new SourceCatalog(List.of(A)), docker, SETTINGS, List.of(failing, seen::add));
        fleet.start();
        await().atMost(Duration.ofSeconds(1)).until(() -> seen.size() == 1);
    }

    @Test
    void snapshotRejectsUnknownSource() {
        fleet = new Fleet(new SourceCatalog(List.of(A)), docker, SETTINGS, List.of());
        assertThatThrownBy(() -> fleet.snapshot("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown sourceId: missing");
    }

    @Test
    void closeStopsMonitorsAndLaterStartDoesNothing() {
        fleet = new Fleet(new SourceCatalog(List.of(A)), docker, SETTINGS, List.of());
        fleet.start();
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.listCalls.get() >= 1);
        fleet.close();
        int calls = docker.listCalls.get();
        fleet.start();
        await().during(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(1))
                .until(() -> docker.listCalls.get() == calls);
    }
}
