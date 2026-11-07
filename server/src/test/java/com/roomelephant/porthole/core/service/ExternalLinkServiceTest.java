package com.roomelephant.porthole.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.core.enrichment.icon.IconSettings;
import com.roomelephant.porthole.core.enrichment.preferences.CustomIconResolver;
import com.roomelephant.porthole.core.model.ExternalLink;
import com.roomelephant.porthole.core.model.ExternalLinkDetails;
import com.roomelephant.porthole.core.model.IconImageType;
import com.roomelephant.porthole.core.model.PresentedLink;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.model.exception.LinkLimitReachedException;
import com.roomelephant.porthole.core.model.exception.LinkNotFoundException;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import com.roomelephant.porthole.core.port.outbound.ExternalLinkStore;
import com.roomelephant.porthole.core.port.outbound.IconFileStore;
import com.roomelephant.porthole.outbound.icons.FilesystemIconFileStore;
import com.roomelephant.porthole.outbound.links.JsonExternalLinkStore;
import com.roomelephant.porthole.outbound.preferences.JsonContainerPreferencesStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ExternalLinkServiceTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
    private static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    private static final ExternalLinkDetails ROUTER = new ExternalLinkDetails("Router", "http://192.168.50.1/", "base");
    private static final CustomIconResolver RESOLVER =
            new CustomIconResolver(new IconSettings("https://cdn.example/webp", ".webp"));

    @TempDir
    Path directory;

    private ContainerPreferencesStore preferences;
    private JsonExternalLinkStore links;
    private FilesystemIconFileStore files;
    private ExternalLinkService service;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder().build();
        preferences = new JsonContainerPreferencesStore(directory.resolve("preferences.json"), mapper);
        links = new JsonExternalLinkStore(directory.resolve("links.json"), mapper);
        files = new FilesystemIconFileStore(directory.resolve("icons"));
        service = new ExternalLinkService(links, new UploadedIcons(files, preferences, links), RESOLVER);
    }

    private SelectedIcon savedIcon(String id) {
        return links.snapshot().stream()
                .filter(link -> link.id().equals(id))
                .findFirst()
                .orElseThrow()
                .icon();
    }

    @Test
    void createGenerates32HexIdAndTrimsValues() {
        PresentedLink created = service.create(new ExternalLinkDetails(" Router ", " http://r/ ", "  "));

        assertThat(created.id()).matches(ExternalLink.ID);
        assertThat(created).isEqualTo(new PresentedLink(created.id(), "Router", "http://r/", null, null));
        assertThat(service.list()).containsExactly(created);
    }

    @Test
    void createRejectsInvalidUrl() {
        assertThatThrownBy(() -> service.create(new ExternalLinkDetails("Router", "javascript:alert(1)", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.list()).isEmpty();
    }

    @Test
    void createPastTheLimitThrowsUntilOneIsDeleted() {
        PresentedLink first = service.create(ROUTER);
        for (int index = 1; index < ExternalLinkService.MAX_LINKS; index++) {
            service.create(ROUTER);
        }

        assertThatThrownBy(() -> service.create(ROUTER)).isInstanceOf(LinkLimitReachedException.class);
        assertThat(service.list()).hasSize(ExternalLinkService.MAX_LINKS);

        service.delete(first.id());
        service.create(ROUTER);
        assertThat(service.list()).hasSize(ExternalLinkService.MAX_LINKS);
    }

    @Test
    void updateReplacesDetailsAndKeepsIcon() {
        String id = service.create(ROUTER).id();
        service.selectLibraryIcon(id, "router");

        PresentedLink updated = service.update(id, new ExternalLinkDetails(" Gateway ", "http://10.0.0.1/", null));

        assertThat(updated)
                .isEqualTo(new PresentedLink(
                        id, "Gateway", "http://10.0.0.1/", null, "https://cdn.example/webp/router.webp"));
    }

    @Test
    void updateValidatesBeforeSaving() {
        String id = service.create(ROUTER).id();

        assertThatThrownBy(() -> service.update(id, new ExternalLinkDetails("", "http://r/", null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.list().getFirst().name()).isEqualTo("Router");
    }

    @Test
    void unknownOrMalformedIdIsNotFound() {
        for (String id : List.of("0123456789abcdef0123456789abcdef", "../x")) {
            assertThatThrownBy(() -> service.update(id, ROUTER)).isInstanceOf(LinkNotFoundException.class);
            assertThatThrownBy(() -> service.delete(id)).isInstanceOf(LinkNotFoundException.class);
            assertThatThrownBy(() -> service.selectLibraryIcon(id, "router")).isInstanceOf(LinkNotFoundException.class);
            assertThatThrownBy(() -> service.uploadIcon(id, PNG)).isInstanceOf(LinkNotFoundException.class);
        }
        assertThat(files.count()).isZero();
    }

    @Test
    void invalidLibraryNameIsRejected() {
        String id = service.create(ROUTER).id();

        assertThatThrownBy(() -> service.selectLibraryIcon(id, "../x")).isInstanceOf(IllegalArgumentException.class);

        assertThat(savedIcon(id)).isNull();
    }

    @Test
    void presentedIconUrlResolvesLibraryAndUpload() {
        String id = service.create(ROUTER).id();
        assertThat(service.list().getFirst().iconUrl()).isNull();

        service.selectLibraryIcon(id, "router");
        assertThat(service.list().getFirst().iconUrl()).isEqualTo("https://cdn.example/webp/router.webp");

        service.uploadIcon(id, PNG);
        assertThat(service.list().getFirst().iconUrl())
                .isEqualTo("/api/icons/" + savedIcon(id).name());
    }

    @Test
    void libraryIconReplacingUploadDeletesOldFile() {
        String id = service.create(ROUTER).id();
        service.uploadIcon(id, PNG);

        service.selectLibraryIcon(id, "router");

        assertThat(files.count()).isZero();
    }

    @Test
    void uploadReplacingUploadDeletesOldFile() {
        String id = service.create(ROUTER).id();
        service.uploadIcon(id, PNG);
        String first = savedIcon(id).name();

        service.uploadIcon(id, WEBP);

        assertThat(files.read(first)).isEmpty();
        assertThat(files.count()).isEqualTo(1);
    }

    @Test
    void deleteRemovesUploadedIconFile() {
        String id = service.create(ROUTER).id();
        service.uploadIcon(id, PNG);

        service.delete(id);

        assertThat(service.list()).isEmpty();
        assertThat(files.count()).isZero();
    }

    @Test
    void deleteStillRemovesTheRecordWhenTheFileCannotBeDeleted() {
        String id = service.create(ROUTER).id();
        service.uploadIcon(id, PNG);
        IconFileStore undeletable = new IconFileStore() {
            @Override
            public String store(byte[] bytes, IconImageType type) {
                return files.store(bytes, type);
            }

            @Override
            public Optional<byte[]> read(String name) {
                return files.read(name);
            }

            @Override
            public void delete(String name) {
                throw new IllegalStateException("cannot delete");
            }

            @Override
            public int count() {
                return files.count();
            }

            @Override
            public void retainOnly(Set<String> names) {
                files.retainOnly(names);
            }
        };
        ExternalLinkService failing =
                new ExternalLinkService(links, new UploadedIcons(undeletable, preferences, links), RESOLVER);

        failing.delete(id);

        assertThat(service.list()).isEmpty();
    }

    @Test
    void linkDeletedDuringUploadLeavesNoFile() {
        String id = service.create(ROUTER).id();
        ExternalLinkStore deletedMidway = new ExternalLinkStore() {
            @Override
            public List<ExternalLink> snapshot() {
                return links.snapshot();
            }

            @Override
            public void add(ExternalLink link) {
                links.add(link);
            }

            @Override
            public ExternalLink replaceDetails(String linkId, ExternalLinkDetails details) {
                return links.replaceDetails(linkId, details);
            }

            @Override
            public void replaceIcon(String linkId, SelectedIcon icon) {
                throw new LinkNotFoundException(linkId);
            }

            @Override
            public ExternalLink remove(String linkId) {
                return links.remove(linkId);
            }
        };
        ExternalLinkService racing =
                new ExternalLinkService(deletedMidway, new UploadedIcons(files, preferences, deletedMidway), RESOLVER);

        assertThatThrownBy(() -> racing.uploadIcon(id, PNG)).isInstanceOf(LinkNotFoundException.class);

        assertThat(files.count()).isZero();
    }
}
