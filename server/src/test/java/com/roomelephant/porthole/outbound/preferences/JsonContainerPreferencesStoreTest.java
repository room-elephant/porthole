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

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n"})
    void blankFileStartsEmpty(String contents) throws IOException {
        var path = directory.resolve("preferences.json");
        Files.writeString(path, contents);
        var store = new JsonContainerPreferencesStore(path, mapper);
        assertThat(store.snapshot()).isEmpty();
        store.replaceSelection(key, custom);
        assertThat(new JsonContainerPreferencesStore(path, mapper).snapshot())
                .containsEntry(key, new ContainerPreferences(custom));
    }

    @Test
    void resettingToAutomaticRemovesTheRecord() throws IOException {
        var path = directory.resolve("preferences.json");
        var store = new JsonContainerPreferencesStore(path, mapper);
        var prometheus = new ContainerPreferencesKey("local", "prometheus");
        store.replaceSelection(key, custom);
        store.replaceSelection(prometheus, new SelectedEndpoint("none", "none", null));
        assertThat(store.replaceSelection(key, null)).isEqualTo(new ContainerPreferences(null));
        var json = mapper.readTree(Files.readString(path));
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.get(0).get("containerName").asString()).isEqualTo("prometheus");
        assertThat(store.snapshot()).doesNotContainKey(key).containsKey(prometheus);
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
        assertThat(store.snapshot()).doesNotContainKey(key);
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
        assertThat(new JsonContainerPreferencesStore(path, mapper)
                        .snapshot()
                        .getOrDefault(key, new ContainerPreferences(null)))
                .isEqualTo(new ContainerPreferences(replacement));
        assertThat(Files.readString(path)).doesNotContain("grafana.example.com", "\"url\"");
    }

    @Test
    void ignoresUnknownFieldsAndDropsThemOnNextSave() throws IOException {
        var path = directory.resolve("preferences.json");
        Files.writeString(path, """
                [{"sourceId":"local","containerName":"grafana","extra":true,"preferences":{
                  "icon":{"url":"https://icons.example.com/grafana.svg"},
                  "selectedEndpoint":{"id":"custom","source":"custom","url":"https://old.example.com"}}}]
                """);
        var store = new JsonContainerPreferencesStore(path, mapper);
        assertThat(store.snapshot())
                .containsEntry(
                        key,
                        new ContainerPreferences(new SelectedEndpoint("custom", "custom", "https://old.example.com")));
        store.replaceSelection(key, new SelectedEndpoint("none", "none", null));
        assertThat(Files.readString(path)).doesNotContain("icons.example.com", "old.example.com", "extra");
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
                "[{\"sourceId\":\"\",\"containerName\":\"grafana\",\"preferences\":{}}]",
                "[{\"sourceId\":\" \",\"containerName\":\"grafana\",\"preferences\":{}}]",
                "[{\"sourceId\":\"local\",\"containerName\":\"\",\"preferences\":{}}]",
                "[{\"sourceId\":\"local\",\"containerName\":\" \",\"preferences\":{}}]",
                "[{\"sourceId\":\"local\",\"containerName\":\"grafana\",\"preferences\":{\"selectedEndpoint\":{}}}]",
                "[{\"sourceId\":\"local\",\"containerName\":\"grafana\",\"preferences\":{\"selectedEndpoint\":{\"id\":\"none\"}}}]"
            })
    void malformedFileFailsWithoutOverwritingIt(String contents) throws IOException {
        var path = directory.resolve("preferences.json");
        Files.writeString(path, contents);
        var originalBytes = Files.readAllBytes(path);
        assertThatThrownBy(() -> new JsonContainerPreferencesStore(path, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(path.toString());
        assertThat(Files.readAllBytes(path)).isEqualTo(originalBytes);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"id\":\"\",\"source\":\"traefik\"}",
                "{\"id\":\" \",\"source\":\"traefik\"}",
                "{\"id\":\"app\",\"source\":\"\"}",
                "{\"id\":\"app\",\"source\":\" \",\"url\":null}",
                "{\"id\":\"custom\",\"source\":\"custom\"}",
                "{\"id\":\"custom\",\"source\":\"custom\",\"url\":null}",
                "{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"\"}",
                "{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"/relative\"}",
                "{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"//app.example\"}",
                "{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"https:/no-host\"}",
                "{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"ftp://app.example\"}",
                "{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"https://bad host\"}",
                "{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"https://user:pass@app.example\"}",
                "{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"https://user@app.example\"}",
                "{\"id\":\"custom\",\"source\":\"traefik\",\"url\":\"https://app.example\"}",
                "{\"id\":\"app\",\"source\":\"custom\",\"url\":\"https://app.example\"}",
                "{\"id\":\"none\",\"source\":\"traefik\"}",
                "{\"id\":\"app\",\"source\":\"none\"}",
                "{\"id\":\"none\",\"source\":\"none\",\"url\":\"https://app.example\"}",
                "{\"id\":\"traefik:app\",\"source\":\"traefik\",\"url\":\"https://app.example\"}"
            })
    void invalidLoadedSelectionFailsWithoutChangingFileBytes(String selection) throws IOException {
        var path = directory.resolve("preferences.json");
        Files.writeString(path, """
                [{"sourceId":"local","containerName":"grafana","preferences":{
                  "futureField":{"nested":"preserve"},"selectedEndpoint":%s}}]
                """.formatted(selection));
        var originalBytes = Files.readAllBytes(path);
        assertThatThrownBy(() -> new JsonContainerPreferencesStore(path, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(path.toString());
        assertThat(Files.readAllBytes(path)).isEqualTo(originalBytes);
    }

    @Test
    void invalidRecordCannotBeHiddenByLaterRecordForTheSameContainer() throws IOException {
        var path = directory.resolve("preferences.json");
        Files.writeString(path, """
                [{"sourceId":"local","containerName":"grafana","preferences":{
                   "selectedEndpoint":{"id":"custom","source":"custom"}}},
                 {"sourceId":"local","containerName":"grafana","preferences":{"selectedEndpoint":null}}]
                """);
        var originalBytes = Files.readAllBytes(path);
        assertThatThrownBy(() -> new JsonContainerPreferencesStore(path, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(path.toString());
        assertThat(Files.readAllBytes(path)).isEqualTo(originalBytes);
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
