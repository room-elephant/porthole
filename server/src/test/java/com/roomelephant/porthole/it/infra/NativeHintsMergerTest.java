package com.roomelephant.porthole.it.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeHintsMergerTest {
    @TempDir
    Path temporary;

    @Test
    void shouldClearPreviousRecordingsWithoutTouchingPublishedMetadata() throws Exception {
        Path recordings = Files.createDirectories(temporary.resolve("recordings"));
        Path old = Files.writeString(recordings.resolve("old.json"), "old run");
        Path published = Files.writeString(temporary.resolve("reachability-metadata.json"), "published");
        NativeHintsMerger.prepare(recordings);
        assertThat(old).doesNotExist();
        assertThat(recordings).isDirectory();
        assertThat(Files.readString(published)).isEqualTo("published");
    }

    @Test
    void shouldNotClearLockedRecordingsFromAnotherRun() throws Exception {
        Path recordings = Files.createDirectory(temporary.resolve("recordings"));
        Path app = Files.createDirectory(recordings.resolve("app-active"));
        Path lock = Files.writeString(app.resolve(".lock"), "active");
        assertThatThrownBy(() -> NativeHintsMerger.prepare(recordings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
        assertThat(Files.readString(lock)).isEqualTo("active");
    }

    @Test
    void shouldRefuseIncompleteRecordingWithoutChangingPublishedMetadata() throws Exception {
        Path recordings = Files.createDirectories(temporary.resolve("recordings"));
        Files.createDirectory(recordings.resolve("app-incomplete"));
        Path output = Files.createDirectories(temporary.resolve("output"));
        Path published = Files.writeString(output.resolve("reachability-metadata.json"), "published");
        assertThatThrownBy(() -> NativeHintsMerger.merge(recordings, output))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Incomplete recording");
        assertThat(Files.readString(published)).isEqualTo("published");
    }

    @Test
    void shouldRefuseActiveRecordingWithoutDeletingItsLock() throws Exception {
        Path recordings = Files.createDirectories(temporary.resolve("recordings"));
        Path app = Files.createDirectory(recordings.resolve("app-active"));
        Files.writeString(app.resolve("reachability-metadata.json"), "{}");
        Path lock = Files.writeString(app.resolve(".lock"), "active");
        Path output = temporary.resolve("output");
        assertThatThrownBy(() -> NativeHintsMerger.merge(recordings, output))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Still recording");
        assertThat(Files.readString(lock)).isEqualTo("active");
    }

    @Test
    void shouldRefuseToPublishWhenThereAreNoRecordings() throws Exception {
        Path recordings = Files.createDirectory(temporary.resolve("recordings"));
        Path output = temporary.resolve("output");
        assertThatThrownBy(() -> NativeHintsMerger.merge(recordings, output))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No native hint recordings");
    }
}
