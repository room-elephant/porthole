package com.roomelephant.porthole.entries;

import static com.roomelephant.porthole.entries.PatchField.absent;
import static com.roomelephant.porthole.entries.PatchField.removed;
import static com.roomelephant.porthole.entries.PatchField.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.DockerTestSources;
import com.roomelephant.porthole.entries.EndpointChoice.Custom;
import com.roomelephant.porthole.entries.EndpointChoice.Discovered;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.Icon.Library;
import com.roomelephant.porthole.entries.Icon.Upload;
import com.roomelephant.porthole.entries.icons.FilesystemIconFileStore;
import com.roomelephant.porthole.entries.store.JsonStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class EntryServiceTest {
    private static final String APP = "container:walker:app";
    private static final String ROUTER_URL = "http://192.168.50.1/";

    @TempDir
    Path directory;

    private IconService icons;
    private FailingStore store;
    private EntryService entries;

    @BeforeEach
    void setUp() {
        icons = new IconService(new FilesystemIconFileStore(directory.resolve("icons"), Clock.systemUTC()));
        store = new FailingStore(stateStore());
        entries = service();
    }

    private JsonStateStore stateStore() {
        return new JsonStateStore(
                directory.resolve("state.json"), JsonMapper.builder().build());
    }

    private EntryService service() {
        return new EntryService(store, icons, DockerTestSources.catalog("walker"), List.of(listener));
    }

    private final CountingListener listener = new CountingListener();

    private static final class CountingListener implements EntryListener {
        private int calls;

        @Override
        public void onEntriesChanged() {
            calls++;
        }
    }

    @Test
    void notifiesListenersAfterEachCommittedWrite() {
        LinkRef router = router();
        entries.update(APP, nameOnly(set("App")));
        entries.delete(router.id());

        assertThat(listener.calls).isEqualTo(3);
    }

    @Test
    void doesNotNotifyWhenAWriteIsRejected() {
        assertThatThrownBy(() -> entries.update("link:" + "0".repeat(32), nameOnly(set("x"))))
                .isInstanceOf(EntryNotFoundException.class);
        assertThatThrownBy(() -> entries.delete(APP)).isInstanceOf(EntryNotDeletableException.class);

        assertThat(listener.calls).isZero();
    }

    @Test
    void aFailingListenerDoesNotFailTheWrite() {
        EntryListener failing = () -> {
            throw new IllegalStateException("boom");
        };
        EntryService service =
                new EntryService(store, icons, DockerTestSources.catalog("walker"), List.of(failing, listener));

        service.update(APP, nameOnly(set("App")));

        assertThat(service.state().find(EntryRef.parse(APP))).isPresent();
        assertThat(listener.calls).isEqualTo(1);
    }

    private EntryState reloaded() {
        return stateStore().load();
    }

    private static EntryPatch patch(
            PatchField<String> name,
            PatchField<Icon> icon,
            PatchField<EndpointChoice> endpoint,
            PatchField<String> stack) {
        return new EntryPatch(name, icon, endpoint, stack);
    }

    private static EntryPatch nameOnly(PatchField<String> name) {
        return patch(name, absent(), absent(), absent());
    }

    private String upload() {
        return icons.upload(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1});
    }

    private LinkRef router() {
        return entries.createLink(new LinkDraft("Router", ROUTER_URL, "base", null));
    }

    @Test
    void createsLinkAndPersists() {
        LinkRef ref = router();

        EntryRecord expected = new EntryRecord(ref, "Router", null, new Custom(ROUTER_URL), "base");
        assertThat(entries.state().find(ref)).contains(expected);
        assertThat(reloaded().find(ref)).contains(expected);
    }

    @Test
    void createLinkTrimsAndBlankStackIsNull() {
        LinkRef ref = entries.createLink(new LinkDraft("  Router ", " " + ROUTER_URL + " ", "  ", null));

        EntryRecord record = entries.state().find(ref).orElseThrow();
        assertThat(record.name()).isEqualTo("Router");
        assertThat(record.endpoint()).isEqualTo(new Custom(ROUTER_URL));
        assertThat(record.stack()).isNull();
    }

    @Test
    void createLinkRejectsMissingNameOrBadUrl() {
        assertThatThrownBy(() -> entries.createLink(new LinkDraft(null, ROUTER_URL, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A link needs a name");
        assertThatThrownBy(() -> entries.createLink(new LinkDraft("Router", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A link needs a URL");
        assertThatThrownBy(() -> entries.createLink(new LinkDraft("Router", "ftp://x", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(entries.state().entries()).isEmpty();
    }

    @Test
    void createLinkAt500Throws() {
        for (int index = 0; index < EntryService.MAX_LINKS; index++) router();

        assertThatThrownBy(this::router)
                .isInstanceOf(LinkLimitReachedException.class)
                .hasMessage("Porthole has reached its limit of 500 links");
        assertThat(entries.state().linkCount()).isEqualTo(500);
    }

    @Test
    void createLinkWithUploadedIconRequiresExistingUpload() {
        String id = upload();

        LinkRef ref = entries.createLink(new LinkDraft("Router", ROUTER_URL, null, new Upload(id)));

        assertThat(entries.state().find(ref).orElseThrow().icon()).isEqualTo(new Upload(id));
        assertThatThrownBy(() -> entries.createLink(
                        new LinkDraft("Router", ROUTER_URL, null, new Upload("e".repeat(64) + ".png"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown icon " + "e".repeat(64) + ".png");
    }

    @Test
    void patchContainerOverridesEachField() {
        String upload = upload();
        ContainerRef ref = new ContainerRef("walker", "app");

        entries.update(
                APP, patch(set("App"), set(new Upload(upload)), set(new Discovered("traefik:https://app/")), absent()));
        assertThat(reloaded().find(ref))
                .contains(
                        new EntryRecord(ref, "App", new Upload(upload), new Discovered("traefik:https://app/"), null));

        entries.update(
                APP, patch(absent(), set(new Library("coredns")), set(new Custom("https://x.example/")), absent()));
        assertThat(entries.state().find(ref))
                .contains(new EntryRecord(ref, "App", new Library("coredns"), new Custom("https://x.example/"), null));

        entries.update(APP, patch(absent(), absent(), set(EndpointChoice.NONE), absent()));
        assertThat(entries.state().find(ref).orElseThrow().endpoint()).isEqualTo(EndpointChoice.NONE);
    }

    @Test
    void patchContainerNullRemovesOverrideAndEmptyRecordDisappears() {
        entries.update(APP, patch(set("App"), set(new Library("coredns")), absent(), absent()));

        entries.update(APP, patch(removed(), absent(), absent(), absent()));
        assertThat(entries.state()
                        .find(new ContainerRef("walker", "app"))
                        .orElseThrow()
                        .name())
                .isNull();

        entries.update(APP, patch(absent(), removed(), absent(), absent()));
        assertThat(entries.state().entries()).isEmpty();
        assertThat(reloaded().entries()).isEmpty();
    }

    @Test
    void patchContainerForUndiscoveredContainerSucceeds() {
        entries.update("container:walker:not-running", nameOnly(set("Later")));

        assertThat(entries.state().find(new ContainerRef("walker", "not-running")))
                .isPresent();
    }

    @Test
    void patchContainerUnknownSourceIs404() {
        assertThatThrownBy(() -> entries.update("container:gone:app", nameOnly(set("App"))))
                .isInstanceOf(EntryNotFoundException.class)
                .hasMessage("No entry container:gone:app");
    }

    @Test
    void patchContainerStackIsRejected() {
        assertThatThrownBy(() -> entries.update(APP, patch(absent(), absent(), absent(), set("base"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Containers take their stack from Docker Compose");
        assertThatThrownBy(() -> entries.update(APP, patch(absent(), absent(), absent(), removed())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Containers take their stack from Docker Compose");
    }

    @Test
    void patchLinkNullNameOrEndpointIsRejected() {
        String id = router().id();

        assertThatThrownBy(() -> entries.update(id, nameOnly(removed())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A link needs a name");
        assertThatThrownBy(() -> entries.update(id, patch(absent(), absent(), removed(), absent())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A link needs a URL");
    }

    @Test
    void patchLinkDiscoveredOrNoneEndpointIsRejected() {
        String id = router().id();

        assertThatThrownBy(() -> entries.update(id, patch(absent(), absent(), set(new Discovered("x")), absent())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A link's endpoint must be a URL");
        assertThatThrownBy(() -> entries.update(id, patch(absent(), absent(), set(EndpointChoice.NONE), absent())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A link's endpoint must be a URL");
    }

    @Test
    void patchLinkChangesNameUrlAndStack() {
        LinkRef ref = router();

        entries.update(ref.id(), patch(set("Gateway"), absent(), set(new Custom("https://gw.example/")), set("net")));

        assertThat(reloaded().find(ref))
                .contains(new EntryRecord(ref, "Gateway", null, new Custom("https://gw.example/"), "net"));
    }

    @Test
    void patchLinkStackNullOrBlankClears() {
        LinkRef ref = router();
        entries.update(ref.id(), patch(absent(), absent(), absent(), removed()));
        assertThat(entries.state().find(ref).orElseThrow().stack()).isNull();

        entries.update(ref.id(), patch(absent(), absent(), absent(), set("base")));
        entries.update(ref.id(), patch(absent(), absent(), absent(), set("   ")));
        assertThat(entries.state().find(ref).orElseThrow().stack()).isNull();
    }

    @Test
    void patchUnknownLinkIs404() {
        String id = "link:" + "0".repeat(32);

        assertThatThrownBy(() -> entries.update(id, nameOnly(set("x"))))
                .isInstanceOf(EntryNotFoundException.class)
                .hasMessage("No entry " + id);
    }

    @Test
    void patchMalformedIdIs404() {
        assertThatThrownBy(() -> entries.update("nonsense", nameOnly(set("x"))))
                .isInstanceOf(EntryNotFoundException.class);
    }

    @Test
    void patchWithUnknownUploadIsRejected() {
        String missing = "f".repeat(64) + ".png";

        assertThatThrownBy(() -> entries.update(APP, patch(absent(), set(new Upload(missing)), absent(), absent())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown icon " + missing);
    }

    @Test
    void renameWorksWhenTheSavedUploadFileIsGone() throws Exception {
        String upload = upload();
        entries.update(APP, patch(absent(), set(new Upload(upload)), absent(), absent()));
        Files.delete(directory.resolve("icons").resolve(upload));

        entries.update(APP, nameOnly(set("App")));

        assertThat(entries.state()
                        .find(new ContainerRef("walker", "app"))
                        .orElseThrow()
                        .name())
                .isEqualTo("App");
    }

    @Test
    void absentFieldsAreKept() {
        LinkRef ref = entries.createLink(new LinkDraft("Router", ROUTER_URL, "base", new Library("router")));

        entries.update(ref.id(), nameOnly(set("Gateway")));

        assertThat(entries.state().find(ref))
                .contains(new EntryRecord(ref, "Gateway", new Library("router"), new Custom(ROUTER_URL), "base"));
    }

    @Test
    void deleteLinkRemovesIt() {
        LinkRef ref = router();

        entries.delete(ref.id());

        assertThat(entries.state().find(ref)).isEmpty();
        assertThat(reloaded().find(ref)).isEmpty();
    }

    @Test
    void deleteContainerIsNotDeletable() {
        entries.update(APP, nameOnly(set("App")));

        assertThatThrownBy(() -> entries.delete(APP))
                .isInstanceOf(EntryNotDeletableException.class)
                .hasMessage("Container entries can't be deleted");
        assertThat(entries.state().entries()).hasSize(1);
    }

    @Test
    void deleteUnknownLinkIs404() {
        assertThatThrownBy(() -> entries.delete("link:" + "0".repeat(32))).isInstanceOf(EntryNotFoundException.class);
    }

    @Test
    void failedWriteKeepsPreviousState() throws Exception {
        entries.update(APP, nameOnly(set("App")));
        String before = Files.readString(directory.resolve("state.json"));
        EntryState state = entries.state();
        store.failNext = true;

        assertThatThrownBy(() -> entries.update(APP, nameOnly(set("Renamed"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to save entries");

        assertThat(entries.state()).isEqualTo(state);
        assertThat(Files.readString(directory.resolve("state.json"))).isEqualTo(before);
        assertThatThrownBy(() -> {
                    store.failNext = true;
                    router();
                })
                .isInstanceOf(IllegalStateException.class);
        assertThat(entries.state()).isEqualTo(state);
    }

    @Test
    void startsWithEntriesForUnconfiguredSources() {
        stateStore()
                .save(EntryState.EMPTY.with(new EntryRecord(new ContainerRef("gone", "app"), "App", null, null, null)));

        EntryService restarted = service();

        assertThat(restarted.state().find(new ContainerRef("gone", "app"))).isPresent();
    }

    @Test
    void startupFailsOnInvalidStateFile() throws Exception {
        Files.writeString(directory.resolve("state.json"), "{\"schemaVersion\":2,\"entries\":[]}");

        assertThatThrownBy(this::service).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentPatchesOfDifferentEntriesAreAllKept() throws Exception {
        List<Future<?>> writes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 20; index++) {
                String id = "container:walker:app-" + index;
                writes.add(pool.submit(() -> entries.update(id, nameOnly(set("App")))));
            }
            for (Future<?> write : writes) write.get();
        }

        assertThat(reloaded().entries()).hasSize(20);
    }

    /** Delegates to a real store, failing the next save on request. */
    private static final class FailingStore implements StateStore {
        private final StateStore delegate;
        private volatile boolean failNext;

        private FailingStore(StateStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public EntryState load() {
            return delegate.load();
        }

        @Override
        public void save(EntryState state) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("Failed to save entries");
            }
            delegate.save(state);
        }
    }
}
