package com.roomelephant.porthole.core.service;

import static org.assertj.core.api.Assertions.*;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.outbound.preferences.JsonContainerPreferencesStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

class ContainerPreferencesServiceTest {
    @TempDir
    Path directory;

    @Test
    void replacesEntireSelectionAndPersistsResetWithoutLookingUpContainer() {
        var path = directory.resolve("preferences.json");
        var store = new JsonContainerPreferencesStore(path, JsonMapper.builder().build());
        var service = new ContainerPreferencesService(store);
        var key = new ContainerPreferencesKey("offline-source", "app");
        var other = new ContainerPreferencesKey("other-source", "app");
        var custom = new SelectedEndpoint("custom", "custom", "HTTPS://app.example/path?query=1#fragment");
        assertThat(service.select(other, custom)).isEqualTo(new ContainerPreferences(custom));
        assertThat(service.select(key, custom)).isEqualTo(new ContainerPreferences(custom));
        var discovered = new SelectedEndpoint("traefik:missing", "traefik", null);
        assertThat(service.select(key, discovered)).isEqualTo(new ContainerPreferences(discovered));
        assertThat(store.snapshot().get(key).selectedEndpoint().url()).isNull();
        var none = new SelectedEndpoint("none", "none", null);
        assertThat(service.select(key, none)).isEqualTo(new ContainerPreferences(none));
        assertThat(service.select(key, null)).isEqualTo(new ContainerPreferences(null));
        var reloaded =
                new JsonContainerPreferencesStore(path, JsonMapper.builder().build());
        assertThat(reloaded.snapshot())
                .containsEntry(key, new ContainerPreferences(null))
                .containsEntry(other, new ContainerPreferences(custom));
    }

    @ParameterizedTest
    @MethodSource("invalidSelections")
    void rejectsMalformedSelectionsBeforeSaving(SelectedEndpoint selection) {
        var store = new JsonContainerPreferencesStore(
                directory.resolve("preferences.json"), JsonMapper.builder().build());
        var service = new ContainerPreferencesService(store);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.select(new ContainerPreferencesKey("local", "app"), selection));
        assertThat(store.snapshot()).isEmpty();
        assertThat(directory.resolve("preferences.json")).doesNotExist();
    }

    static Stream<SelectedEndpoint> invalidSelections() {
        return Stream.of(
                new SelectedEndpoint(null, "traefik", null),
                new SelectedEndpoint("", "traefik", null),
                new SelectedEndpoint(" ", "traefik", null),
                new SelectedEndpoint("a", null, null),
                new SelectedEndpoint("a", "", null),
                new SelectedEndpoint("a", " ", null),
                new SelectedEndpoint("a", "traefik", "https://app.example"),
                new SelectedEndpoint("custom", "traefik", null),
                new SelectedEndpoint("a", "custom", "https://app.example"),
                new SelectedEndpoint("none", "traefik", null),
                new SelectedEndpoint("a", "none", null),
                new SelectedEndpoint("none", "none", "https://app.example"),
                new SelectedEndpoint("custom", "custom", null),
                new SelectedEndpoint("custom", "custom", ""),
                new SelectedEndpoint("custom", "custom", "/relative"),
                new SelectedEndpoint("custom", "custom", "//app.example"),
                new SelectedEndpoint("custom", "custom", "ftp://app.example"),
                new SelectedEndpoint("custom", "custom", "https:/no-host"),
                new SelectedEndpoint("custom", "custom", "https://user:pass@app.example"),
                new SelectedEndpoint("custom", "custom", "https://user@app.example"),
                new SelectedEndpoint("custom", "custom", "https://bad host"));
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    void rejectsMissingOrBlankIdentity(ContainerPreferencesKey key) {
        var store = new JsonContainerPreferencesStore(
                directory.resolve("preferences.json"), JsonMapper.builder().build());
        assertThatIllegalArgumentException().isThrownBy(() -> new ContainerPreferencesService(store).select(key, null));
        assertThat(store.snapshot()).isEmpty();
    }

    static Stream<ContainerPreferencesKey> invalidKeys() {
        return Stream.of(
                new ContainerPreferencesKey(null, "app"),
                new ContainerPreferencesKey("", "app"),
                new ContainerPreferencesKey(" ", "app"),
                new ContainerPreferencesKey("local", null),
                new ContainerPreferencesKey("local", ""),
                new ContainerPreferencesKey("local", " "));
    }

    @Test
    void propagatesWriteFailureAndRetainsConfirmedPreferences() throws IOException {
        var path = directory.resolve("preferences.json");
        var store = new JsonContainerPreferencesStore(path, JsonMapper.builder().build());
        var service = new ContainerPreferencesService(store);
        var key = new ContainerPreferencesKey("local", "app");
        service.select(key, new SelectedEndpoint("custom", "custom", "https://confirmed.example"));
        var confirmedBytes = Files.readAllBytes(path);
        var permissions = Files.getPosixFilePermissions(directory);
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"));
        try {
            assertThatIllegalStateException().isThrownBy(() -> service.select(key, null));
            assertThat(store.snapshot().get(key).selectedEndpoint().url()).isEqualTo("https://confirmed.example");
            assertThat(Files.readAllBytes(path)).isEqualTo(confirmedBytes);
        } finally {
            Files.setPosixFilePermissions(directory, permissions);
        }
    }
}
