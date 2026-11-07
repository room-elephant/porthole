package com.roomelephant.porthole.outbound.links;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.core.model.ExternalLink;
import com.roomelephant.porthole.core.model.ExternalLinkDetails;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.model.exception.LinkNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class JsonExternalLinkStoreTest {
    private static final String ID = "3f2a9c1e5b7d4e8a9c217d4e5f6a8b90";
    private static final String OTHER_ID = "0123456789abcdef0123456789abcdef";
    private static final ExternalLink ROUTER =
            new ExternalLink(ID, "Router", "http://192.168.50.1/", "base", SelectedIcon.library("router"));
    private static final ExternalLink NAS = new ExternalLink(OTHER_ID, "NAS", "https://nas.lan:5001/", null, null);

    @TempDir
    Path directory;

    private Path file() {
        return directory.resolve("links.json");
    }

    private JsonExternalLinkStore store() {
        return new JsonExternalLinkStore(file(), JsonMapper.builder().build());
    }

    @Test
    void missingFileIsEmpty() {
        assertThat(store().snapshot()).isEmpty();
        assertThat(file()).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  \n"})
    void blankFileIsEmpty(String contents) throws IOException {
        Files.writeString(file(), contents);

        assertThat(store().snapshot()).isEmpty();
    }

    @Test
    void addSurvivesReload() {
        JsonExternalLinkStore store = store();

        store.add(ROUTER);
        store.add(NAS);

        assertThat(store.snapshot()).containsExactly(ROUTER, NAS);
        assertThat(store().snapshot()).containsExactly(ROUTER, NAS);
    }

    @Test
    void absentStackAndIconAreNotWritten() throws IOException {
        store().add(NAS);

        assertThat(Files.readString(file())).doesNotContain("stack").doesNotContain("icon");
    }

    @Test
    void snapshotIsImmutableAndUnchangedByLaterSaves() {
        JsonExternalLinkStore store = store();
        store.add(ROUTER);
        var before = store.snapshot();

        store.add(NAS);

        assertThat(before).containsExactly(ROUTER);
        assertThatThrownBy(() -> before.add(NAS)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void replaceDetailsKeepsIcon() {
        JsonExternalLinkStore store = store();
        store.add(ROUTER);

        ExternalLink updated = store.replaceDetails(ID, new ExternalLinkDetails("Gateway", "http://10.0.0.1/", null));

        assertThat(updated).isEqualTo(new ExternalLink(ID, "Gateway", "http://10.0.0.1/", null, ROUTER.icon()));
        assertThat(store().snapshot()).containsExactly(updated);
    }

    @Test
    void replaceIconKeepsDetails() {
        JsonExternalLinkStore store = store();
        store.add(ROUTER);
        SelectedIcon icon = SelectedIcon.upload(OTHER_ID + ".png");

        store.replaceIcon(ID, icon);

        assertThat(store().snapshot()).containsExactly(ROUTER.withIcon(icon));
    }

    @Test
    void removeReturnsTheLinkAndKeepsOthers() {
        JsonExternalLinkStore store = store();
        store.add(ROUTER);
        store.add(NAS);

        assertThat(store.remove(ID)).isEqualTo(ROUTER);

        assertThat(store().snapshot()).containsExactly(NAS);
    }

    @Test
    void unknownIdThrows() {
        JsonExternalLinkStore store = store();
        ExternalLinkDetails details = new ExternalLinkDetails("Router", "http://r/", null);

        assertThatThrownBy(() -> store.replaceDetails(ID, details)).isInstanceOf(LinkNotFoundException.class);
        assertThatThrownBy(() -> store.replaceIcon(ID, SelectedIcon.library("router")))
                .isInstanceOf(LinkNotFoundException.class);
        assertThatThrownBy(() -> store.remove(ID)).isInstanceOf(LinkNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not json",
                "{}",
                "null",
                "[null]",
                "[{\"id\":\"3f2a9c1e5b7d4e8a9c217d4e5f6a8b90\",\"name\":\"R\",\"url\":\"javascript:alert(1)\"}]",
                "[{\"id\":\"bad\",\"name\":\"R\",\"url\":\"http://r/\"}]",
                "[{\"id\":\"3f2a9c1e5b7d4e8a9c217d4e5f6a8b90\",\"url\":\"http://r/\"}]"
            })
    void invalidFileFailsStartupWithoutChangingIt(String contents) throws IOException {
        Files.writeString(file(), contents);

        assertThatThrownBy(this::store).isInstanceOf(IllegalStateException.class);

        assertThat(file()).hasContent(contents);
    }

    @Test
    void duplicateIdInFileFailsStartup() throws IOException {
        String record = "{\"id\":\"%s\",\"name\":\"R\",\"url\":\"http://r/\"}".formatted(ID);
        Files.writeString(file(), "[" + record + "," + record + "]");

        assertThatThrownBy(this::store).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedWriteKeepsPriorSnapshotAndFile() throws IOException {
        JsonExternalLinkStore store = store();
        store.add(ROUTER);
        String saved = Files.readString(file());
        Files.delete(file());
        Files.createDirectory(file());
        Files.writeString(file().resolve("child"), "blocks the atomic move");

        assertThatThrownBy(() -> store.add(NAS)).isInstanceOf(IllegalStateException.class);

        assertThat(store.snapshot()).containsExactly(ROUTER);
        Files.delete(file().resolve("child"));
        Files.delete(file());
        Files.writeString(file(), saved);
        assertThat(store().snapshot()).containsExactly(ROUTER);
    }
}
