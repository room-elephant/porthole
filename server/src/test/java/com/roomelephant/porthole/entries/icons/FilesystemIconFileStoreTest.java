package com.roomelephant.porthole.entries.icons;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.testsupport.MutableClock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemIconFileStoreTest {
    private static final String A = "a".repeat(64) + ".png";
    private static final String B = "b".repeat(64) + ".svg";
    private static final Instant START = Instant.parse("2026-11-08T10:00:00Z");

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock(START);

    private FilesystemIconFileStore store() {
        return new FilesystemIconFileStore(directory.resolve("icons"), clock);
    }

    @Test
    void storeIsAtomicAndIdempotent() throws IOException {
        FilesystemIconFileStore store = store();

        assertThat(store.store(A, new byte[] {1, 2})).isTrue();
        assertThat(store.store(A, new byte[] {9})).isFalse();

        assertThat(store.read(A)).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(1, 2));
        try (var files = Files.list(directory.resolve("icons"))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly(A);
        }
    }

    @Test
    void storedFilesTakeTheClocksTime() throws IOException {
        store().store(A, new byte[] {1});

        assertThat(Files.getLastModifiedTime(directory.resolve("icons").resolve(A)))
                .isEqualTo(FileTime.from(START));
    }

    @Test
    void touchRefreshesAgeAndReportsMissing() throws IOException {
        FilesystemIconFileStore store = store();
        store.store(A, new byte[] {1});
        clock.advance(Duration.ofHours(2));

        assertThat(store.touch(A)).isTrue();
        assertThat(store.touch(B)).isFalse();
        assertThat(Files.getLastModifiedTime(directory.resolve("icons").resolve(A)))
                .isEqualTo(FileTime.from(START.plus(Duration.ofHours(2))));
    }

    @Test
    void readRejectsNamesThatAreNotUploadIds() throws IOException {
        Files.createDirectories(directory.resolve("icons"));
        Files.writeString(directory.resolve("state.json"), "{}");

        assertThat(store().read("../state.json")).isEmpty();
        assertThat(store().read("a".repeat(32) + ".png")).isEmpty();
        assertThat(store().read(A)).isEmpty();
        assertThat(store().touch("../state.json")).isFalse();
    }

    @Test
    void deleteUnreferencedRemovesOldUnkeptFilesOnly() {
        FilesystemIconFileStore store = store();
        store.store(A, new byte[] {1});
        clock.advance(Duration.ofHours(2));
        store.store(B, new byte[] {2});
        String kept = "c".repeat(64) + ".jpg";
        store.store(kept, new byte[] {3});
        clock.advance(Duration.ofMinutes(30));

        int deleted = store.deleteUnreferenced(Set.of(kept), clock.instant().minus(Duration.ofHours(1)));

        assertThat(deleted).isEqualTo(1);
        assertThat(store.read(A)).isEmpty();
        assertThat(store.read(B)).isPresent();
        assertThat(store.read(kept)).isPresent();
    }

    @Test
    void deleteUnreferencedIgnoresForeignFiles() throws IOException {
        Path icons = Files.createDirectories(directory.resolve("icons"));
        Files.writeString(icons.resolve("notes.txt"), "mine");
        Files.setLastModifiedTime(icons.resolve("notes.txt"), FileTime.from(START.minus(Duration.ofDays(1))));

        store().deleteUnreferenced(Set.of(), START);

        assertThat(icons.resolve("notes.txt")).exists();
    }

    @Test
    void deleteUnreferencedRemovesStaleTempFiles() throws IOException {
        Path icons = Files.createDirectories(directory.resolve("icons"));
        Path temp = icons.resolve(".icon-123.tmp");
        Files.write(temp, new byte[] {1});
        Files.setLastModifiedTime(temp, FileTime.from(START.minus(Duration.ofDays(1))));

        assertThat(store().deleteUnreferenced(Set.of(), START)).isEqualTo(1);
        assertThat(temp).doesNotExist();
    }

    @Test
    void countCountsOnlyUploads() throws IOException {
        FilesystemIconFileStore store = store();
        store.store(A, new byte[] {1});
        store.store(B, new byte[] {2});
        Files.writeString(directory.resolve("icons").resolve("notes.txt"), "x");

        assertThat(store.count()).isEqualTo(2);
    }

    @Test
    void missingDirectoryIsEmpty() {
        FilesystemIconFileStore store = store();

        assertThat(store.count()).isZero();
        assertThat(store.deleteUnreferenced(Set.of(), START)).isZero();
        assertThat(store.read(A)).isEmpty();
    }
}
