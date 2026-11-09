package com.roomelephant.porthole.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {
    @TempDir
    Path directory;

    @Test
    void createsMissingParentsAndWritesTheFile() throws IOException {
        Path target = directory.resolve("nested/file.json");

        AtomicFiles.replace(target, "new".getBytes(), ".file-");

        assertThat(target).hasContent("new");
    }

    @Test
    void replacesExistingContentAndLeavesNoTemporaryFile() throws IOException {
        Path target = directory.resolve("file.json");
        Files.writeString(target, "a much longer old value");

        AtomicFiles.replace(target, "new".getBytes(), ".file-");

        assertThat(target).hasContent("new");
        try (var files = Files.list(directory)) {
            assertThat(files).containsExactly(target);
        }
    }

    @Test
    void removesTheTemporaryFileWhenTheTargetCannotBeReplaced() throws IOException {
        Path target = directory.resolve("occupied");
        Files.createDirectory(target);
        Files.writeString(target.resolve("child"), "x");

        assertThatThrownBy(() -> AtomicFiles.replace(target, "new".getBytes(), ".file-"))
                .isInstanceOf(IOException.class);

        try (var files = Files.list(directory)) {
            assertThat(files).containsExactly(target);
        }
    }

    @Test
    void failsWhenTheParentCannotBeCreated() throws IOException {
        Path blocker = directory.resolve("blocker");
        Files.writeString(blocker, "x");

        assertThatThrownBy(() -> AtomicFiles.replace(blocker.resolve("file.json"), "new".getBytes(), ".file-"))
                .isInstanceOf(IOException.class);
    }
}
