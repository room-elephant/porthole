package com.roomelephant.porthole.outbound.icons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.core.model.IconImageType;
import com.roomelephant.porthole.core.model.SelectedIcon;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemIconFileStoreTest {
    private static final byte[] BYTES = {1, 2, 3};

    @TempDir
    Path directory;

    private Path icons() {
        return directory.resolve("data/icons");
    }

    private FilesystemIconFileStore store() {
        return new FilesystemIconFileStore(icons());
    }

    @Test
    void storeCreatesFolderAndLeavesOnlyTheNamedFile() throws IOException {
        var store = store();
        String name = store.store(BYTES, IconImageType.WEBP);
        assertThat(name).matches(SelectedIcon.UPLOAD_NAME).endsWith(".webp");
        try (var files = Files.list(icons())) {
            assertThat(files.toList()).containsExactly(icons().resolve(name));
        }
        assertThat(store.read(name))
                .hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(BYTES));
    }

    @Test
    void eachStoreGetsANewName() {
        var store = store();
        assertThat(store.store(BYTES, IconImageType.PNG)).isNotEqualTo(store.store(BYTES, IconImageType.PNG));
    }

    @Test
    void readOfMissingFileOrFolderIsEmpty() {
        assertThat(store().read("0123456789abcdef0123456789abcdef.png")).isEmpty();
    }

    @Test
    void readAndDeleteRefuseNamesOutsideTheUploadPattern() throws IOException {
        var store = store();
        store.store(BYTES, IconImageType.PNG);
        Path outside = directory.resolve("data/preferences.json");
        Path inside = icons().resolve("my-logo.png");
        Files.writeString(outside, "secret");
        Files.writeString(inside, "the user's own image");

        assertThat(store.read("../preferences.json")).isEmpty();
        assertThat(store.read("my-logo.png")).isEmpty();
        store.delete("../preferences.json");
        store.delete("my-logo.png");

        assertThat(outside).exists();
        assertThat(inside).exists();
    }

    @Test
    void deleteRemovesFileAndIgnoresMissingOne() {
        var store = store();
        String name = store.store(BYTES, IconImageType.PNG);
        store.delete(name);
        store.delete(name);
        assertThat(store.read(name)).isEmpty();
    }

    @Test
    void countOnlyIncludesUploadNames() throws IOException {
        var store = store();
        assertThat(store.count()).isZero();
        store.store(BYTES, IconImageType.PNG);
        Files.writeString(icons().resolve(".icon-leftover.tmp"), "partial");
        assertThat(store.count()).isEqualTo(1);
    }

    @Test
    void retainOnlyKeepsNamedFilesAndRemovesStrayTempFiles() throws IOException {
        var store = store();
        String kept = store.store(BYTES, IconImageType.PNG);
        String removed = store.store(BYTES, IconImageType.PNG);
        Path stray = icons().resolve(".icon-leftover.tmp");
        Files.writeString(stray, "partial");
        store.retainOnly(Set.of(kept));
        assertThat(store.read(kept)).isPresent();
        assertThat(store.read(removed)).isEmpty();
        assertThat(stray).doesNotExist();
    }

    @Test
    void retainOnlyLeavesFilesItDidNotCreate() throws IOException {
        var store = store();
        store.store(BYTES, IconImageType.PNG);
        Path mine = icons().resolve("my-logo.png");
        Path other = icons().resolve("notes.txt");
        Files.writeString(mine, "the user's own image");
        Files.writeString(other, "the user's own notes");
        store.retainOnly(Set.of());
        assertThat(store.count()).isZero();
        assertThat(mine).exists();
        assertThat(other).exists();
    }

    @Test
    void retainOnlyWithMissingFolderDoesNothing() {
        store().retainOnly(Set.of());
        assertThat(icons()).doesNotExist();
    }

    @Test
    void storeFailureIsReportedAndLeavesNothingBehind() throws IOException {
        Files.createDirectories(directory.resolve("data"));
        Files.writeString(icons(), "a file where the folder should be");
        assertThatThrownBy(() -> store().store(BYTES, IconImageType.PNG)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void readDeleteCountAndRetainReportFailures() throws IOException {
        Files.createDirectories(directory.resolve("data"));
        Files.writeString(icons(), "a file where the folder should be");
        var store = store();
        assertThatThrownBy(store::count).isInstanceOf(UncheckedIOException.class);
        assertThatThrownBy(() -> store.retainOnly(Set.of())).isInstanceOf(UncheckedIOException.class);
        assertThat(store.read("0123456789abcdef0123456789abcdef.png")).isEmpty();
        store.delete("0123456789abcdef0123456789abcdef.png");
    }
}
