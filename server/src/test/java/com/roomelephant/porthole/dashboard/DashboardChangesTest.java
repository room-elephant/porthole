package com.roomelephant.porthole.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionChange;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionChecks;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.fleet.ContainerKey;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class DashboardChangesTest {
    private static final ContainerRef APP = new ContainerRef("lab-a", "app");
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
        changes.onEntriesChanged(APP, null);
        changes.onVersionsChanged(new VersionChange.Run(new VersionChecks(true, null)));

        assertThat(changes.revision()).isEqualTo(START + 3);
    }

    @Test
    void coalescesChangesInAWindow() {
        changes.onSnapshot(snapshot());
        changes.onEntriesChanged(APP, null);
        changes.onVersionsChanged(new VersionChange.Run(new VersionChecks(true, null)));
        assertThat(delivered).isEmpty();

        scheduled(1).getFirst().run();

        assertThat(delivered).containsExactly(START + 3);
    }

    @Test
    void deliversAgainAfterTheWindow() {
        changes.onEntriesChanged(APP, null);
        scheduled(1).getFirst().run();
        changes.onEntriesChanged(APP, null);
        scheduled(2).getLast().run();

        assertThat(delivered).containsExactly(START + 1, START + 2);
    }

    @Test
    void unsubscribedReceivesNothing() {
        List<Long> other = new ArrayList<>();
        Runnable unsubscribe = changes.subscribe(other::add);
        unsubscribe.run();

        changes.onEntriesChanged(APP, null);
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

        failingFirst.onEntriesChanged(APP, null);
        scheduled(1).getFirst().run();

        assertThat(delivered).containsExactly(START + 1);
    }

    @Test
    void aStoppedSchedulerStillCountsChanges() {
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RejectedExecutionException("closed"));

        changes.onEntriesChanged(APP, null);
        changes.onEntriesChanged(APP, null);

        assertThat(changes.revision()).isEqualTo(START + 2);
        assertThat(delivered).isEmpty();
    }

    @Test
    void closeStopsTheScheduler() {
        changes.close();

        verify(scheduler).shutdownNow();
    }

    private final Logger logger = (Logger) LoggerFactory.getLogger(DashboardChanges.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    private List<String> logLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @AfterEach
    void detachLog() {
        logger.detachAppender(logged);
        logger.setLevel(null);
    }

    @Test
    void logsEachRevisionWithItsCauseAtDebug() {
        logged.start();
        logger.addAppender(logged);
        logger.setLevel(Level.DEBUG);

        changes.onSnapshot(snapshot());
        changes.onEntriesChanged(APP, new EntryRecord(APP, "App", null, null, null));
        changes.onEntriesChanged(APP, null);
        changes.onVersionsChanged(
                new VersionChange.Result(new ContainerKey("lab-a", "abc"), null, new VersionInfo("1.0", "1.1", true)));
        changes.onVersionsChanged(new VersionChange.Run(new VersionChecks(true, null)));
        changes.onVersionsChanged(new VersionChange.Run(new VersionChecks(false, Instant.EPOCH)));

        assertThat(logLines())
                .containsExactly(
                        "Dashboard revision " + (START + 1) + ": walker DOWN (Waiting for first check)",
                        "Dashboard revision " + (START + 2) + ": entry container:lab-a:app saved: "
                                + new EntryRecord(APP, "App", null, null, null),
                        "Dashboard revision " + (START + 3) + ": entry container:lab-a:app deleted",
                        "Dashboard revision " + (START + 4) + ": version of lab-a/abc: null → "
                                + new VersionInfo("1.0", "1.1", true),
                        "Dashboard revision " + (START + 5) + ": version check started",
                        "Dashboard revision " + (START + 6) + ": version check finished at 1970-01-01T00:00:00Z");
    }
}
