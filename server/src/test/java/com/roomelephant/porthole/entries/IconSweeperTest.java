package com.roomelephant.porthole.entries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.Icon.Upload;
import com.roomelephant.porthole.entries.icons.FilesystemIconFileStore;
import com.roomelephant.porthole.testsupport.MutableClock;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class IconSweeperTest {
    private static final EntriesProperties PROPERTIES =
            new EntriesProperties(Path.of("unused"), Duration.ofMinutes(15), Duration.ofHours(1));

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-08T10:00:00Z"));

    private static byte[] png(int seed) {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, (byte) seed};
    }

    @Test
    void sweepKeepsReferencedUploads() {
        IconService icons = new IconService(new FilesystemIconFileStore(directory, clock));
        String referenced = icons.upload(png(1));
        String orphan = icons.upload(png(2));
        EntryService entries = mock(EntryService.class);
        when(entries.state())
                .thenReturn(EntryState.EMPTY.with(
                        new EntryRecord(new ContainerRef("walker", "app"), null, new Upload(referenced), null, null)));
        clock.advance(Duration.ofHours(2));

        new IconSweeper(entries, icons, PROPERTIES, clock).sweep();

        assertThat(icons.read(referenced)).isPresent();
        assertThat(icons.read(orphan)).isEmpty();
    }

    @Test
    void sweepFailureIsLoggedNotThrown() {
        IconService icons = mock(IconService.class);
        when(icons.sweep(any(), any(), any())).thenThrow(new IllegalStateException("disk gone"));
        EntryService entries = mock(EntryService.class);
        when(entries.state()).thenReturn(EntryState.EMPTY);

        assertThatCode(() -> new IconSweeper(entries, icons, PROPERTIES, clock).sweep())
                .doesNotThrowAnyException();
    }

    @Test
    void startSchedulesAnImmediateSweepOnTheIntervalAndCloseStops() {
        IconService icons = mock(IconService.class);
        EntryService entries = mock(EntryService.class);
        when(entries.state()).thenReturn(EntryState.EMPTY);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        AtomicInteger created = new AtomicInteger();
        IconSweeper sweeper = new IconSweeper(entries, icons, PROPERTIES, clock, () -> {
            created.incrementAndGet();
            return scheduler;
        });

        sweeper.start();
        sweeper.start();

        assertThat(created).hasValue(1);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler)
                .scheduleWithFixedDelay(
                        task.capture(), eq(0L), eq(Duration.ofMinutes(15).toNanos()), eq(TimeUnit.NANOSECONDS));
        task.getValue().run();
        verify(icons).sweep(Set.of(), clock.instant(), Duration.ofHours(1));

        sweeper.close();
        sweeper.close();
        sweeper.start();

        verify(scheduler, times(2)).shutdownNow();
        assertThat(created).hasValue(1);
    }

    @Test
    void newSchedulerRunsOnADaemonThread() throws Exception {
        ScheduledExecutorService scheduler = IconSweeper.newScheduler();
        try {
            Thread thread = scheduler.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);

            assertThat(thread.isDaemon()).isTrue();
            assertThat(thread.getName()).isEqualTo("icon-sweep");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void startAfterCloseDoesNothing() {
        IconService icons = mock(IconService.class);
        IconSweeper sweeper = new IconSweeper(mock(EntryService.class), icons, PROPERTIES, clock);

        sweeper.close();
        sweeper.start();

        org.mockito.Mockito.verifyNoInteractions(icons);
    }
}
