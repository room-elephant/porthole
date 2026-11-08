package com.roomelephant.porthole.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.roomelephant.porthole.fleet.Source;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DashboardChangesTest {
    private static final long START = 1_762_596_000_000L;
    private static final Duration WINDOW = Duration.ofMillis(250);

    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final List<Long> delivered = new ArrayList<>();
    private DashboardChanges changes;

    @BeforeEach
    void setUp() {
        changes = new DashboardChanges(Clock.fixed(Instant.ofEpochMilli(START), ZoneOffset.UTC), scheduler, WINDOW);
        changes.subscribe(delivered::add);
    }

    private List<Runnable> scheduled(int count) {
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(count)).schedule(tasks.capture(), eq(WINDOW.toNanos()), eq(TimeUnit.NANOSECONDS));
        return tasks.getAllValues();
    }

    private static SourceSnapshot snapshot() {
        return SourceSnapshot.initial(new Source("walker", "Walker", null, true, Duration.ofSeconds(1)));
    }

    @Test
    void startsAtTheStartTime() {
        assertThat(changes.revision()).isEqualTo(START);
    }

    @Test
    void eachSourceBumpsTheRevision() {
        changes.onSnapshot(snapshot());
        changes.onEntriesChanged();
        changes.onVersionsChanged();

        assertThat(changes.revision()).isEqualTo(START + 3);
    }

    @Test
    void coalescesChangesInAWindow() {
        changes.onSnapshot(snapshot());
        changes.onEntriesChanged();
        changes.onVersionsChanged();
        assertThat(delivered).isEmpty();

        scheduled(1).getFirst().run();

        assertThat(delivered).containsExactly(START + 3);
    }

    @Test
    void deliversAgainAfterTheWindow() {
        changes.onEntriesChanged();
        scheduled(1).getFirst().run();
        changes.onEntriesChanged();
        scheduled(2).getLast().run();

        assertThat(delivered).containsExactly(START + 1, START + 2);
    }

    @Test
    void unsubscribedReceivesNothing() {
        List<Long> other = new ArrayList<>();
        Runnable unsubscribe = changes.subscribe(other::add);
        unsubscribe.run();

        changes.onEntriesChanged();
        scheduled(1).getFirst().run();

        assertThat(other).isEmpty();
        assertThat(delivered).containsExactly(START + 1);
    }

    @Test
    void aFailingSubscriberDoesNotStopOthers() {
        DashboardChanges failingFirst =
                new DashboardChanges(Clock.fixed(Instant.ofEpochMilli(START), ZoneOffset.UTC), scheduler, WINDOW);
        failingFirst.subscribe(_ -> {
            throw new IllegalStateException("gone");
        });
        failingFirst.subscribe(delivered::add);

        failingFirst.onEntriesChanged();
        scheduled(1).getFirst().run();

        assertThat(delivered).containsExactly(START + 1);
    }

    @Test
    void aStoppedSchedulerStillCountsChanges() {
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RejectedExecutionException("closed"));

        changes.onEntriesChanged();
        changes.onEntriesChanged();

        assertThat(changes.revision()).isEqualTo(START + 2);
        assertThat(delivered).isEmpty();
    }

    @Test
    void closeStopsTheScheduler() {
        changes.close();

        verify(scheduler).shutdownNow();
    }
}
