package com.roomelephant.porthole.outbound.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class JsonContainerPreferencesStoreTest {
    @TempDir
    Path directory;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ContainerPreferencesKey key = new ContainerPreferencesKey("local", "grafana");
    private final SelectedEndpoint custom = new SelectedEndpoint("custom", "custom", "https://grafana.example.com");

    @Test
    void missingFileStartsEmptyWithoutWriting() {
        var path = directory.resolve("data/preferences.json");
        assertThat(new JsonContainerPreferencesStore(path, mapper).snapshot()).isEmpty();
        assertThat(path).doesNotExist();
    }

    @Test
    void snapshotReadsMemoryWithoutReloadingTheFile() throws IOException {
        var path = directory.resolve("preferences.json");
        var store = new JsonContainerPreferencesStore(path, mapper);
        store.replaceSelection(key, custom);
        Files.writeString(path, "changed externally");
        assertThat(store.snapshot()).containsEntry(key, new ContainerPreferences(custom));
    }

    @Test
    void selectionIsCommittedAndSurvivesReconstruction() throws IOException {
        var path = directory.resolve("data/preferences.json");
        var store = new JsonContainerPreferencesStore(path, mapper);
        assertThat(store.replaceSelection(key, custom)).isEqualTo(new ContainerPreferences(custom));
        assertThat(new JsonContainerPreferencesStore(path, mapper).snapshot())
                .containsEntry(key, new ContainerPreferences(custom));
        var json = mapper.readTree(Files.readString(path));
        assertThat(json.isArray()).isTrue();
        assertThat(json.get(0).get("sourceId").asString()).isEqualTo("local");
        assertThat(json.get(0).get("containerName").asString()).isEqualTo("grafana");
        assertThat(json.get(0)
                        .get("preferences")
                        .get("selectedEndpoint")
                        .get("url")
                        .asString())
                .isEqualTo("https://grafana.example.com");
    }

    @Test
    void snapshotsAreImmutableAndDoNotChangeAfterLaterSaves() {
        var store = new JsonContainerPreferencesStore(directory.resolve("preferences.json"), mapper);
        store.replaceSelection(key, custom);
        var snapshot = store.snapshot();
        assertThatThrownBy(() -> snapshot.clear()).isInstanceOf(UnsupportedOperationException.class);
        store.replaceSelection(key, null);
        assertThat(snapshot).containsEntry(key, new ContainerPreferences(custom));
        assertThat(store.snapshot()).containsEntry(key, new ContainerPreferences(null));
    }

    @Test
    void sameContainerNameOnDifferentSourcesRemainsIndependent() {
        var path = directory.resolve("preferences.json");
        var store = new JsonContainerPreferencesStore(path, mapper);
        store.replaceSelection(key, custom);
        var remoteKey = new ContainerPreferencesKey("remote", "grafana");
        var none = new SelectedEndpoint("none", "none", null);
        store.replaceSelection(remoteKey, none);
        assertThat(new JsonContainerPreferencesStore(path, mapper).snapshot())
                .containsEntry(key, new ContainerPreferences(custom))
                .containsEntry(remoteKey, new ContainerPreferences(none));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"published-port", "none"})
    void replacingCustomSelectionDiscardsItsUrl(String source) throws IOException {
        var path = directory.resolve("preferences.json");
        var store = new JsonContainerPreferencesStore(path, mapper);
        store.replaceSelection(key, custom);
        var replacement = source == null
                ? null
                : new SelectedEndpoint(source.equals("none") ? "none" : "published-port:3000", source, null);
        store.replaceSelection(key, replacement);
        assertThat(new JsonContainerPreferencesStore(path, mapper).snapshot())
                .containsEntry(key, new ContainerPreferences(replacement));
        assertThat(Files.readString(path)).doesNotContain("grafana.example.com", "\"url\"");
    }

    @Test
    void preservesUnrelatedPreferenceFields() throws IOException {
        var path = directory.resolve("preferences.json");
        Files.writeString(path, """
                [{"sourceId":"local","containerName":"grafana","preferences":{
                  "icon":{"url":"https://icons.example.com/grafana.svg"},
                  "selectedEndpoint":{"id":"custom","source":"custom","url":"https://old.example.com"}}}]
                """);
        var store = new JsonContainerPreferencesStore(path, mapper);
        store.replaceSelection(key, new SelectedEndpoint("none", "none", null));
        var preferences = mapper.readTree(Files.readString(path)).get(0).get("preferences");
        assertThat(preferences.get("icon").get("url").asString()).isEqualTo("https://icons.example.com/grafana.svg");
        assertThat(Files.readString(path)).doesNotContain("old.example.com");
    }

    @Test
    void acceptsAbsentAndNullSelectionsAsAutomatic() throws IOException {
        var path = directory.resolve("preferences.json");
        Files.writeString(path, """
                [{"sourceId":"local","containerName":"grafana","preferences":{}},
                 {"sourceId":"remote","containerName":"grafana","preferences":{"selectedEndpoint":null}}]
                """);
        assertThat(new JsonContainerPreferencesStore(path, mapper).snapshot())
                .containsEntry(key, new ContainerPreferences(null))
                .containsEntry(new ContainerPreferencesKey("remote", "grafana"), new ContainerPreferences(null));
    }

    @Test
    void concurrentUpdatesToDifferentContainersBothSurviveReconstruction() throws Exception {
        var path = directory.resolve("preferences.json");
        var store = new JsonContainerPreferencesStore(path, mapper);
        var other = new ContainerPreferencesKey("local", "prometheus");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                start.await();
                return store.replaceSelection(key, custom);
            });
            var second = executor.submit(() -> {
                start.await();
                return store.replaceSelection(other, new SelectedEndpoint("none", "none", null));
            });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
        assertThat(new JsonContainerPreferencesStore(path, mapper).snapshot())
                .hasSize(2)
                .containsEntry(key, new ContainerPreferences(custom))
                .containsEntry(other, new ContainerPreferences(new SelectedEndpoint("none", "none", null)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "broken json",
                "null",
                "{}",
                "[null]",
                "[{}]",
                "[{\"sourceId\":\"local\",\"containerName\":\"grafana\",\"preferences\":null}]",
                "[{\"sourceId\":\"local\",\"preferences\":{}}]",
                "[{\"containerName\":\"grafana\",\"preferences\":{}}]",
                "[{\"sourceId\":\"local\",\"containerName\":\"grafana\",\"preferences\":{\"selectedEndpoint\":{}}}]",
                "[{\"sourceId\":\"local\",\"containerName\":\"grafana\",\"preferences\":{\"selectedEndpoint\":{\"id\":\"none\"}}}]"
            })
    void malformedFileFailsWithoutOverwritingIt(String contents) throws IOException {
        var path = directory.resolve("preferences.json");
        Files.writeString(path, contents);
        assertThatThrownBy(() -> new JsonContainerPreferencesStore(path, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(path.toString());
        assertThat(Files.readString(path)).isEqualTo(contents);
    }

    @Test
    void unreadableExistingPathFailsStartup() throws IOException {
        var path = Files.createDirectory(directory.resolve("preferences.json"));
        assertThatThrownBy(() -> new JsonContainerPreferencesStore(path, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(path.toString());
    }

    @Test
    void failedWriteRetainsPreviousSnapshotAndBytes() throws IOException {
        var path = directory.resolve("preferences.json");
        var store = new JsonContainerPreferencesStore(path, mapper);
        store.replaceSelection(key, custom);
        var savedBytes = Files.readAllBytes(path);
        var savedSnapshot = store.snapshot();
        var permissions = Files.getPosixFilePermissions(directory);
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"));
        try {
            assertThatThrownBy(() -> store.replaceSelection(key, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(path.toString());
            assertThat(store.snapshot()).isEqualTo(savedSnapshot);
            assertThat(Files.readAllBytes(path)).isEqualTo(savedBytes);
        } finally {
            Files.setPosixFilePermissions(directory, permissions);
        }
        try (var files = Files.list(directory)) {
            assertThat(files.toList()).containsExactly(path);
        }
    }

    @Test
    void failedAtomicMoveCleansTemporaryFileAndRetainsSnapshot() throws IOException {
        var path = directory.resolve("preferences.json");
        var store = new JsonContainerPreferencesStore(path, mapper);
        Files.createDirectory(path);
        var sentinel = path.resolve("keep.txt");
        Files.writeString(sentinel, "existing destination");
        assertThatThrownBy(() -> store.replaceSelection(key, custom))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(path.toString());
        assertThat(store.snapshot()).isEmpty();
        assertThat(Files.readString(sentinel)).isEqualTo("existing destination");
        try (var files = Files.list(directory)) {
            assertThat(files.toList()).containsExactly(path);
        }
    }
}
