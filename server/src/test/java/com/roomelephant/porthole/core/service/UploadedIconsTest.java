package com.roomelephant.porthole.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.ExternalLink;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.model.exception.IconLimitReachedException;
import com.roomelephant.porthole.entries.IconImageType;
import com.roomelephant.porthole.outbound.icons.FilesystemIconFileStore;
import com.roomelephant.porthole.outbound.links.JsonExternalLinkStore;
import com.roomelephant.porthole.outbound.preferences.JsonContainerPreferencesStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class UploadedIconsTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
    private static final String LINK_ID = "3f2a9c1e5b7d4e8a9c217d4e5f6a8b90";

    @TempDir
    Path directory;

    private JsonContainerPreferencesStore preferences;
    private JsonExternalLinkStore links;
    private FilesystemIconFileStore files;
    private UploadedIcons uploads;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder().build();
        preferences = new JsonContainerPreferencesStore(directory.resolve("preferences.json"), mapper);
        links = new JsonExternalLinkStore(directory.resolve("links.json"), mapper);
        files = new FilesystemIconFileStore(directory.resolve("icons"));
        uploads = new UploadedIcons(files, preferences, links);
    }

    private SelectedIcon upload() {
        AtomicReference<@Nullable SelectedIcon> saved = new AtomicReference<>();
        uploads.saveUpload(PNG, () -> null, saved::set);
        return saved.get();
    }

    @Test
    void removeUnreferencedKeepsFilesLinksAndContainersUse() {
        SelectedIcon forContainer = upload();
        preferences.replaceIcon(new ContainerPreferencesKey("walker", "app"), forContainer);
        SelectedIcon forLink = upload();
        links.add(new ExternalLink(LINK_ID, "Router", "http://r/", null, forLink));
        String orphan = files.store(PNG, IconImageType.PNG);

        uploads.removeUnreferenced();

        assertThat(files.read(forContainer.name())).isPresent();
        assertThat(files.read(forLink.name())).isPresent();
        assertThat(files.read(orphan)).isEmpty();
    }

    @Test
    void removeUnreferencedIgnoresLibraryIconsAndLinksWithoutIcons() {
        links.add(new ExternalLink(LINK_ID, "Router", "http://r/", null, null));
        links.add(new ExternalLink(
                "0123456789abcdef0123456789abcdef", "NAS", "http://n/", null, SelectedIcon.library("nas")));
        String orphan = files.store(PNG, IconImageType.PNG);

        uploads.removeUnreferenced();

        assertThat(files.read(orphan)).isEmpty();
    }

    @Test
    void failedCleanupDoesNotStopStartup() throws IOException {
        Files.writeString(directory.resolve("icons"), "a file where the folder should be");

        uploads.removeUnreferenced();

        assertThat(directory.resolve("icons")).isRegularFile();
    }

    @Test
    void limitCountsEveryUploadWhoeverOwnsIt() {
        for (int index = 0; index < UploadedIcons.MAX_UPLOADS - 1; index++) {
            preferences.replaceIcon(new ContainerPreferencesKey("walker", "app-" + index), upload());
        }
        links.add(new ExternalLink(LINK_ID, "Router", "http://r/", null, null));
        uploads.saveUpload(PNG, () -> null, icon -> links.replaceIcon(LINK_ID, icon));

        assertThatThrownBy(this::upload).isInstanceOf(IconLimitReachedException.class);
        assertThat(files.count()).isEqualTo(UploadedIcons.MAX_UPLOADS);
    }

    @Test
    void persistFailureDeletesNewFileAndKeepsOld() {
        SelectedIcon previous = upload();

        assertThatThrownBy(() -> uploads.saveUpload(PNG, () -> previous, _ -> {
                    throw new IllegalStateException("disk full");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(files.count()).isEqualTo(1);
        assertThat(files.read(previous.name())).isPresent();
    }

    @Test
    void failingCurrentLookupStoresNothing() {
        assertThatThrownBy(() -> uploads.saveUpload(
                        PNG,
                        () -> {
                            throw new IllegalArgumentException("unknown");
                        },
                        _ -> {}))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(files.count()).isZero();
    }

    @Test
    void deleteQuietlyIgnoresLibraryAndMissingIcons() {
        SelectedIcon stored = upload();

        uploads.deleteQuietly(null);
        uploads.deleteQuietly(SelectedIcon.library(stored.name().substring(0, 8)));

        assertThat(files.read(stored.name())).isPresent();
    }

    @Test
    void deleteQuietlyRemovesAnUpload() {
        SelectedIcon stored = upload();

        uploads.deleteQuietly(stored);

        assertThat(files.read(stored.name())).isEmpty();
    }

    @Test
    void readReturnsBytesAndContentType() {
        assertThat(uploads.read(upload().name())).hasValueSatisfying(icon -> {
            assertThat(icon.bytes()).isEqualTo(PNG);
            assertThat(icon.contentType()).isEqualTo("image/png");
        });
    }

    @Test
    void readOfUnknownFileIsEmpty() {
        assertThat(uploads.read("0123456789abcdef0123456789abcdef.png")).isEmpty();
    }

    @Test
    void readRejectsInvalidNameWithoutTouchingFiles() throws IOException {
        Files.createDirectories(directory.resolve("icons"));
        Files.writeString(directory.resolve("icons/abc.svg"), "<svg/>");

        assertThat(uploads.read("../preferences.json")).isEmpty();
        assertThat(uploads.read("abc.svg")).isEmpty();
    }
}
