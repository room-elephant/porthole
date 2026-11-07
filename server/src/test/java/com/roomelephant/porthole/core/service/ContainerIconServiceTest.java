package com.roomelephant.porthole.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.DockerSource;
import com.roomelephant.porthole.core.model.IconImageType;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.model.exception.IconLimitReachedException;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import com.roomelephant.porthole.core.port.outbound.IconFileStore;
import com.roomelephant.porthole.outbound.icons.FilesystemIconFileStore;
import com.roomelephant.porthole.outbound.preferences.JsonContainerPreferencesStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ContainerIconServiceTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
    private static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

    @TempDir
    Path directory;

    private final ContainerPreferencesKey key = new ContainerPreferencesKey("walker", "app");
    private final DockerOperations docker = mock(DockerOperations.class);
    private JsonContainerPreferencesStore preferences;
    private FilesystemIconFileStore files;
    private ContainerIconService service;

    @BeforeEach
    void setUp() {
        when(docker.sources())
                .thenReturn(List.of(new DockerSource(
                        "walker", "Walker", null, Duration.ofSeconds(1), Duration.ofSeconds(1), false)));
        preferences = new JsonContainerPreferencesStore(
                directory.resolve("preferences.json"), JsonMapper.builder().build());
        files = new FilesystemIconFileStore(iconFolder());
        service = new ContainerIconService(preferences, docker, files);
    }

    private Path iconFolder() {
        return directory.resolve("icons");
    }

    private @Nullable SelectedIcon currentIcon() {
        return currentIcon(key);
    }

    private @Nullable SelectedIcon currentIcon(ContainerPreferencesKey forKey) {
        ContainerPreferences saved = preferences.snapshot().get(forKey);
        return saved == null ? null : saved.icon();
    }

    @Test
    void selectLibraryIconSavesItAndDeletesPreviousUpload() {
        service.uploadIcon(key, PNG);
        String uploaded = currentIcon().name();
        service.selectLibraryIcon(key, "coredns");
        assertThat(currentIcon()).isEqualTo(SelectedIcon.library("coredns"));
        assertThat(files.read(uploaded)).isEmpty();
    }

    @Test
    void selectLibraryIconKeepsSelectedEndpoint() {
        SelectedEndpoint none = new SelectedEndpoint("none", "none", null);
        preferences.replaceSelection(key, none);
        service.selectLibraryIcon(key, "coredns");
        assertThat(preferences.snapshot().get(key))
                .isEqualTo(new ContainerPreferences(none, SelectedIcon.library("coredns")));
    }

    @Test
    void selectLibraryIconRejectsUnknownSource() {
        ContainerPreferencesKey unknown = new ContainerPreferencesKey("elsewhere", "app");
        assertThatThrownBy(() -> service.selectLibraryIcon(unknown, "coredns"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("elsewhere");
        assertThat(preferences.snapshot()).isEmpty();
    }

    @Test
    void selectLibraryIconRejectsInvalidName() {
        assertThatThrownBy(() -> service.selectLibraryIcon(key, "../x")).isInstanceOf(IllegalArgumentException.class);
        assertThat(preferences.snapshot()).isEmpty();
    }

    @Test
    void uploadedSvgIsStoredWithoutItsScripts() {
        service.uploadIcon(
                key,
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script><circle r=\"1\"/></svg>"
                        .getBytes(StandardCharsets.UTF_8));
        SelectedIcon icon = currentIcon();
        assertThat(icon.name()).matches(SelectedIcon.UPLOAD_NAME).endsWith(".svg");
        var stored = service.read(icon.name()).orElseThrow();
        assertThat(stored.contentType()).isEqualTo("image/svg+xml");
        assertThat(new String(stored.bytes(), StandardCharsets.UTF_8))
                .contains("<circle")
                .doesNotContain("script");
    }

    @Test
    void svgWithNothingToDrawIsRejected() {
        byte[] scriptOnly =
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.uploadIcon(key, scriptOnly)).isInstanceOf(IllegalArgumentException.class);
        assertThat(preferences.snapshot()).isEmpty();
    }

    @Test
    void uploadStoresFileAndSavesRandomName() {
        service.uploadIcon(key, WEBP);
        SelectedIcon icon = currentIcon();
        assertThat(icon.selectsUpload()).isTrue();
        assertThat(icon.name()).matches(SelectedIcon.UPLOAD_NAME).endsWith(".webp");
        assertThat(files.read(icon.name()))
                .hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(WEBP));
    }

    @Test
    void uploadReplacingUploadDeletesOldFile() {
        service.uploadIcon(key, PNG);
        String first = currentIcon().name();
        service.uploadIcon(key, WEBP);
        assertThat(files.count()).isEqualTo(1);
        assertThat(files.read(first)).isEmpty();
    }

    @Test
    void uploadRejectsDisguisedAndEmptyFiles() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.uploadIcon(key, html)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.uploadIcon(key, new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThat(files.count()).isZero();
        assertThat(preferences.snapshot()).isEmpty();
    }

    @Test
    void uploadRejectsUnknownSourceWithoutStoringAFile() {
        ContainerPreferencesKey unknown = new ContainerPreferencesKey("elsewhere", "app");
        assertThatThrownBy(() -> service.uploadIcon(unknown, PNG)).isInstanceOf(IllegalArgumentException.class);
        assertThat(files.count()).isZero();
    }

    @Test
    void uploadRejectsBlankContainerNameWithoutStoringAFile() {
        ContainerPreferencesKey blank = new ContainerPreferencesKey("walker", " ");
        assertThatThrownBy(() -> service.uploadIcon(blank, PNG)).isInstanceOf(IllegalArgumentException.class);
        assertThat(files.count()).isZero();
    }

    @Test
    void hostileContainerNameNeverReachesFilePath() throws IOException {
        ContainerPreferencesKey hostile = new ContainerPreferencesKey("walker", "../../evil");
        service.uploadIcon(hostile, PNG);
        try (var stored = Files.list(iconFolder())) {
            assertThat(stored.toList())
                    .singleElement()
                    .satisfies(path -> assertThat(path.getFileName().toString()).matches(SelectedIcon.UPLOAD_NAME));
        }
        try (var root = Files.list(directory)) {
            assertThat(root.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("icons", "preferences.json");
        }
    }

    @Test
    void uploadRejectedAtLimit() {
        for (int index = 0; index < ContainerIconService.MAX_UPLOADS; index++) {
            service.uploadIcon(new ContainerPreferencesKey("walker", "app-" + index), PNG);
        }
        assertThatThrownBy(() -> service.uploadIcon(key, PNG)).isInstanceOf(IconLimitReachedException.class);
        assertThat(files.count()).isEqualTo(500);
        assertThat(currentIcon()).isNull();
    }

    @Test
    void replacingAtLimitIsAllowed() {
        for (int index = 0; index < ContainerIconService.MAX_UPLOADS; index++) {
            service.uploadIcon(new ContainerPreferencesKey("walker", "app-" + index), PNG);
        }
        ContainerPreferencesKey owner = new ContainerPreferencesKey("walker", "app-0");
        service.uploadIcon(owner, WEBP);
        assertThat(files.count()).isEqualTo(500);
        assertThat(currentIcon(owner).name()).endsWith(".webp");
    }

    @Test
    void failedPersistRemovesNewFileAndKeepsOldIcon() {
        service.uploadIcon(key, PNG);
        SelectedIcon previous = currentIcon();
        ContainerIconService failing = new ContainerIconService(new FailingPreferences(preferences), docker, files);
        assertThatThrownBy(() -> failing.uploadIcon(key, WEBP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("disk full");
        assertThat(currentIcon()).isEqualTo(previous);
        assertThat(files.count()).isEqualTo(1);
        assertThat(files.read(previous.name())).isPresent();
    }

    @Test
    void failedPersistOnLibraryPickKeepsOldUpload() {
        service.uploadIcon(key, PNG);
        SelectedIcon previous = currentIcon();
        ContainerIconService failing = new ContainerIconService(new FailingPreferences(preferences), docker, files);
        assertThatThrownBy(() -> failing.selectLibraryIcon(key, "coredns")).isInstanceOf(IllegalStateException.class);
        assertThat(files.read(previous.name())).isPresent();
    }

    @Test
    void failedOldFileDeleteDoesNotFailSave() {
        service.uploadIcon(key, PNG);
        ContainerIconService failing = new ContainerIconService(preferences, docker, new UndeletableFiles(files));
        failing.selectLibraryIcon(key, "coredns");
        assertThat(currentIcon()).isEqualTo(SelectedIcon.library("coredns"));
    }

    @Test
    void readReturnsBytesAndContentType() {
        service.uploadIcon(key, WEBP);
        assertThat(service.read(currentIcon().name())).hasValueSatisfying(icon -> {
            assertThat(icon.bytes()).isEqualTo(WEBP);
            assertThat(icon.contentType()).isEqualTo("image/webp");
        });
    }

    @Test
    void readOfUnknownFileIsEmpty() {
        assertThat(service.read("0123456789abcdef0123456789abcdef.png")).isEmpty();
    }

    @Test
    void readRejectsInvalidNameWithoutTouchingFiles() throws IOException {
        Files.createDirectories(iconFolder());
        Files.writeString(iconFolder().resolve("abc.svg"), "<svg/>");
        assertThat(service.read("../preferences.json")).isEmpty();
        assertThat(service.read("abc.svg")).isEmpty();
    }

    @Test
    void removeUnreferencedUploadsKeepsReferencedFiles() {
        service.uploadIcon(key, PNG);
        String referenced = currentIcon().name();
        String orphan = files.store(PNG, IconImageType.PNG);
        preferences.replaceSelection(
                new ContainerPreferencesKey("walker", "no-icon"), new SelectedEndpoint("none", "none", null));
        service.selectLibraryIcon(new ContainerPreferencesKey("walker", "library"), "coredns");
        service.removeUnreferencedUploads();
        assertThat(files.read(referenced)).isPresent();
        assertThat(files.read(orphan)).isEmpty();
    }

    @Test
    void failedCleanupDoesNotStopStartup() throws IOException {
        Files.writeString(iconFolder(), "a file where the folder should be");
        service.removeUnreferencedUploads();
        assertThat(iconFolder()).isRegularFile();
    }

    private record FailingPreferences(ContainerPreferencesStore delegate) implements ContainerPreferencesStore {
        @Override
        public Map<ContainerPreferencesKey, ContainerPreferences> snapshot() {
            return delegate.snapshot();
        }

        @Override
        public ContainerPreferences replaceSelection(
                ContainerPreferencesKey key, @Nullable SelectedEndpoint selection) {
            throw new IllegalStateException("disk full");
        }

        @Override
        public ContainerPreferences replaceIcon(ContainerPreferencesKey key, SelectedIcon icon) {
            throw new IllegalStateException("disk full");
        }
    }

    private record UndeletableFiles(IconFileStore delegate) implements IconFileStore {
        @Override
        public String store(byte[] bytes, IconImageType type) {
            return delegate.store(bytes, type);
        }

        @Override
        public Optional<byte[]> read(String name) {
            return delegate.read(name);
        }

        @Override
        public void delete(String name) {
            throw new IllegalStateException("cannot delete");
        }

        @Override
        public int count() {
            return delegate.count();
        }

        @Override
        public void retainOnly(Set<String> names) {
            delegate.retainOnly(names);
        }
    }
}
