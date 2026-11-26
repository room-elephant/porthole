package com.roomelephant.porthole.entries.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.entries.EndpointChoice;
import com.roomelephant.porthole.entries.EndpointChoice.Custom;
import com.roomelephant.porthole.entries.EndpointChoice.Discovered;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.EntryState;
import com.roomelephant.porthole.entries.Icon.Library;
import com.roomelephant.porthole.entries.Icon.Upload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class JsonStateStoreTest {
    private static final LinkRef ROUTER = new LinkRef("3f2a9c1e5b7d4e8a9c217d4e5f6a8b90");
    private static final String UPLOAD = "c".repeat(64) + ".png";

    @TempDir
    Path directory;

    private Path file() {
        return directory.resolve("state.json");
    }

    private JsonStateStore store() {
        return new JsonStateStore(file(), JsonMapper.builder().build());
    }

    private void write(String json) throws IOException {
        Files.writeString(file(), json);
    }

    @Test
    void missingFileLoadsEmpty() {
        assertThat(store().load()).isEqualTo(EntryState.EMPTY);
    }

    @Test
    void savesThenLoadsEveryFieldShape() {
        EntryState state = EntryState.EMPTY
                .with(new EntryRecord(
                        new ContainerRef("walker", "app"),
                        "App",
                        new Library("coredns"),
                        new Discovered("traefik:https://app/"),
                        null))
                .with(new EntryRecord(new ContainerRef("walker", "db"), null, null, EndpointChoice.NONE, null))
                .with(new EntryRecord(
                        new ContainerRef("mike", "web"), null, null, new Custom("https://web.example/"), null))
                .with(new EntryRecord(
                        ROUTER, "Router", new Upload(UPLOAD), new Custom("http://192.168.50.1/"), "base"));

        store().save(state);

        assertThat(store().load()).isEqualTo(state);
    }

    @Test
    void omitsNullFields() throws IOException {
        store().save(EntryState.EMPTY.with(
                new EntryRecord(ROUTER, "Router", null, new Custom("http://192.168.50.1/"), null)));

        String text = Files.readString(file());
        assertThat(text).doesNotContain("\"icon\"", "\"stack\"", "null");
        assertThat(text).contains("\"endpoint\":{\"url\":\"http://192.168.50.1/\"}");
    }

    @Test
    void writesSchemaVersion1() throws IOException {
        store().save(EntryState.EMPTY);

        assertThat(Files.readString(file())).isEqualTo("{\"schemaVersion\":1,\"entries\":[]}");
    }

    @Test
    void writesNoneEndpointAsString() throws IOException {
        store().save(EntryState.EMPTY.with(
                new EntryRecord(new ContainerRef("walker", "db"), null, null, EndpointChoice.NONE, null)));

        assertThat(Files.readString(file())).contains("\"endpoint\":\"none\"");
    }

    @Test
    void failsOnSchemaVersionAboveBuild() throws IOException {
        write("{\"schemaVersion\":2,\"entries\":[]}");

        assertThatThrownBy(() -> store().load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schemaVersion 2");
    }

    @Test
    void failsOnMissingSchemaVersion() throws IOException {
        write("{\"entries\":[]}");

        assertThatThrownBy(() -> store().load()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnSchemaVersionBelowOne() throws IOException {
        write("{\"schemaVersion\":0,\"entries\":[]}");

        assertThatThrownBy(() -> store().load()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnMissingEntries() throws IOException {
        write("{\"schemaVersion\":1}");

        assertThatThrownBy(() -> store().load()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnInvalidJson() throws IOException {
        write("{\"schemaVersion\":1,");

        assertThatThrownBy(() -> store().load()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnEmptyFile() throws IOException {
        write("");

        assertThatThrownBy(() -> store().load()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnNonObjectDocument() throws IOException {
        write("[]");

        assertThatThrownBy(() -> store().load()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnInvalidRecord() throws IOException {
        write("{\"schemaVersion\":1,\"entries\":[{\"id\":\"" + ROUTER.id()
                + "\",\"endpoint\":{\"url\":\"http://x/\"}}]}");

        assertThatThrownBy(() -> store().load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A link needs a name");
    }

    @Test
    void failsOnMalformedId() throws IOException {
        write("{\"schemaVersion\":1,\"entries\":[{\"id\":\"thing:x\",\"name\":\"x\"}]}");

        assertThatThrownBy(() -> store().load()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnDuplicateIds() throws IOException {
        write("{\"schemaVersion\":1,\"entries\":[{\"id\":\"container:walker:a\",\"name\":\"x\"},"
                + "{\"id\":\"container:walker:a\",\"name\":\"y\"}]}");

        assertThatThrownBy(() -> store().load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate entry id");
    }

    @Test
    void failsOnUnknownEndpointShape() throws IOException {
        write("{\"schemaVersion\":1,\"entries\":[{\"id\":\"container:walker:a\",\"endpoint\":{\"foo\":1}}]}");

        assertThatThrownBy(() -> store().load()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnUnknownIconShape() throws IOException {
        write("{\"schemaVersion\":1,\"entries\":[{\"id\":\"container:walker:a\",\"icon\":{\"emoji\":\"x\"}}]}");

        assertThatThrownBy(() -> store().load()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnNonStringName() throws IOException {
        write("{\"schemaVersion\":1,\"entries\":[{\"id\":\"container:walker:a\",\"name\":3}]}");

        assertThatThrownBy(() -> store().load()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadsContainerEntriesForAnySourceId() throws IOException {
        write("{\"schemaVersion\":1,\"entries\":[{\"id\":\"container:gone:app\",\"name\":\"App\"}]}");

        assertThat(store().load().find(new ContainerRef("gone", "app")))
                .map(EntryRecord::name)
                .contains("App");
    }

    @Test
    void saveReplacesFileAtomically() throws IOException {
        store().save(EntryState.EMPTY);
        store().save(EntryState.EMPTY.with(new EntryRecord(new ContainerRef("walker", "a"), "A", null, null, null)));

        try (Stream<Path> files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("state.json");
        }
    }

    @Test
    void saveCreatesMissingDirectory() {
        JsonStateStore nested = new JsonStateStore(
                directory.resolve("nested/state.json"), JsonMapper.builder().build());

        nested.save(EntryState.EMPTY);

        assertThat(directory.resolve("nested/state.json")).exists();
    }

    @Test
    void saveFailureThrowsWithoutPath() throws IOException {
        Path blocker = directory.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        JsonStateStore broken = new JsonStateStore(
                blocker.resolve("state.json"), JsonMapper.builder().build());

        assertThatThrownBy(() -> broken.save(EntryState.EMPTY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to save entries");
    }
}
