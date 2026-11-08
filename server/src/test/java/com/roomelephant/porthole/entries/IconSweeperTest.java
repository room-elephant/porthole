package com.roomelephant.porthole.entries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.Icon.Upload;
import com.roomelephant.porthole.entries.icons.FilesystemIconFileStore;
import com.roomelephant.porthole.testsupport.MutableClock;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void startSchedulesAndCloseStops() throws Exception {
        IconService icons = mock(IconService.class);
        EntryService entries = mock(EntryService.class);
        when(entries.state()).thenReturn(EntryState.EMPTY);
        when(icons.sweep(Set.of(), clock.instant(), Duration.ofHours(1))).thenReturn(0);
        IconSweeper sweeper = new IconSweeper(entries, icons, PROPERTIES, clock);

        sweeper.start();
        sweeper.start();
        org.mockito.Mockito.verify(icons, org.mockito.Mockito.timeout(2000).times(1))
                .sweep(Set.of(), clock.instant(), Duration.ofHours(1));
        sweeper.close();
        sweeper.close();
        sweeper.start();
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
