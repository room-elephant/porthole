package com.roomelephant.porthole.versions.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.roomelephant.porthole.shared.ImageReference;
import com.roomelephant.porthole.versions.*;
import java.time.Duration;
import java.util.*;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

class RegistryOperationsAdapterTest {
    private static final RegistryProperties.Cache CACHE = new RegistryProperties.Cache(Duration.ofMinutes(5), 100);
    private static final String A = "sha256:" + "a".repeat(64), B = "sha256:" + "b".repeat(64);

    private final java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();

    private static ImageReference ref(String name) {
        return ImageReference.tryParse(name).orElseThrow();
    }

    @Test
    void providersAndAliasesHaveSeparateCompleteCatalogueCaches() {
        var hub = new Provider("docker.io", List.of("latest", "2.0"));
        var ghcr = new Provider("ghcr.io", List.of("3.0"));
        var adapter = new RegistryOperationsAdapter(List.of(ghcr, hub), CACHE);
        assertEquals(
                new RegistryLookup.Available<>(new TagCatalogue(List.of("latest", "2.0"))),
                adapter.listTags(ref("nginx:1.0")));
        assertEquals(
                new RegistryLookup.Available<>(new TagCatalogue(List.of("3.0"))),
                adapter.listTags(ref("ghcr.io/library/nginx:1.0")));
        hub.result = new RegistryLookup.Available<>(new TagCatalogue(List.of("4.0")));
        assertEquals(
                new RegistryLookup.Available<>(new TagCatalogue(List.of("latest", "2.0"))),
                adapter.listTags(ref("index.docker.io/library/nginx:latest")));
        adapter.invalidateCache();
        assertEquals(hub.result, adapter.listTags(ref("nginx")));
        assertEquals(2, hub.catalogues);
    }

    @Test
    void emptyCataloguesAreCachedForTheirTtl() {
        var hub = new Provider("docker.io", List.of());
        var adapter = new RegistryOperationsAdapter(List.of(hub), CACHE, clock::get);
        adapter.listTags(ref("nginx"));
        clock.addAndGet(Duration.ofMinutes(4).toNanos());
        adapter.listTags(ref("nginx"));
        assertEquals(1, hub.catalogues);
        clock.addAndGet(Duration.ofMinutes(2).toNanos());
        adapter.listTags(ref("nginx"));
        assertEquals(2, hub.catalogues);
    }

    @Test
    void aFailedCatalogueIsSharedForAMinuteThenRetried() {
        var hub = new Provider("docker.io", List.of());
        hub.result = new RegistryLookup.Unavailable<>("Registry catalogue exceeded the page limit");
        var adapter = new RegistryOperationsAdapter(List.of(hub), CACHE, clock::get);

        assertEquals(hub.result, adapter.listTags(ref("nginx:1.29-alpine")));
        clock.addAndGet(Duration.ofSeconds(59).toNanos());
        assertEquals(hub.result, adapter.listTags(ref("nginx:1.29")));
        assertEquals(1, hub.catalogues, "containers checked within the minute share the failure");

        clock.addAndGet(Duration.ofSeconds(2).toNanos());
        hub.result = new RegistryLookup.Available<>(new TagCatalogue(List.of("2.0")));
        assertEquals(hub.result, adapter.listTags(ref("nginx")));
        assertEquals(2, hub.catalogues);
    }

    @Test
    void invalidatingRetriesAFailedCatalogueAtOnce() {
        var hub = new Provider("docker.io", List.of());
        hub.result = new RegistryLookup.Unavailable<>("offline");
        var adapter = new RegistryOperationsAdapter(List.of(hub), CACHE, clock::get);
        adapter.listTags(ref("nginx"));
        adapter.invalidateCache();
        adapter.listTags(ref("nginx"));
        assertEquals(2, hub.catalogues);
    }

    @Test
    void containersOnTheSameImageShareManifestReadsForAMinute() {
        var hub = new Provider("docker.io", List.of());
        var adapter = new RegistryOperationsAdapter(List.of(hub), CACHE, clock::get);
        var image = ref("nginx");

        var first = adapter.readManifests(image, List.of(A));
        var second = adapter.readManifests(ref("docker.io/library/nginx:latest"), List.of(A));

        assertEquals(first, second);
        assertEquals(2, hub.manifests, "one read of the tag and one of the installed digest");
    }

    @Test
    void manifestResultsRefreshAfterAMinuteAndPreserveMissingInstalledMetadata() {
        var hub = new Provider("docker.io", List.of());
        var adapter = new RegistryOperationsAdapter(List.of(hub), CACHE, clock::get);
        var image = ref("nginx");
        var first = adapter.readManifests(image, List.of(A));
        assertInstanceOf(RegistryLookup.Missing.class, first.installed().get(A));
        hub.remote = A;
        clock.addAndGet(Duration.ofSeconds(61).toNanos());
        var second = adapter.readManifests(image, List.of(A));
        assertTrue(second.installed().isEmpty());
        assertEquals(3, hub.manifests);
    }

    @Test
    void invalidatingRereadsManifests() {
        var hub = new Provider("docker.io", List.of());
        var adapter = new RegistryOperationsAdapter(List.of(hub), CACHE, clock::get);
        adapter.readManifests(ref("nginx"), List.of(A));
        adapter.invalidateCache();
        adapter.readManifests(ref("nginx"), List.of(A));
        assertEquals(4, hub.manifests);
    }

    @Test
    void unsupportedPinsAndAmbiguousProvidersAreExplicit() {
        var hub = new Provider("docker.io", List.of());
        var adapter = new RegistryOperationsAdapter(List.of(hub), CACHE);
        assertInstanceOf(RegistryLookup.Unsupported.class, adapter.listTags(ref("ghcr.io/acme/app")));
        assertInstanceOf(RegistryLookup.Unsupported.class, adapter.listTags(ref("nginx@" + A)));
        assertInstanceOf(
                RegistryLookup.Unavailable.class,
                adapter.readManifests(ref("nginx"), List.of()).tagged());
        var ambiguous = new RegistryOperationsAdapter(List.of(hub, hub), CACHE);
        var image = ref("nginx");
        assertThrows(IllegalStateException.class, () -> ambiguous.listTags(image));
    }

    @Test
    void registeredProvidersDoNotRequireACoreAllowlist() {
        var provider = new Provider("quay.io", List.of("4.0"));
        var adapter = new RegistryOperationsAdapter(List.of(provider), CACHE);
        assertEquals(provider.result, adapter.listTags(ref("quay.io/acme/app:1.0")));
    }

    @NullMarked
    private static final class Provider implements RegistryClient {
        private final String registry;
        private RegistryLookup<TagCatalogue> result;
        private int catalogues;
        private int manifests;
        private String remote = B;

        Provider(String registry, List<String> tags) {
            this.registry = registry;
            this.result = new RegistryLookup.Available<>(new TagCatalogue(tags));
        }

        @Override
        public boolean supports(ImageReference image) {
            return registry.equals(image.registry());
        }

        @Override
        public RegistryLookup<TagCatalogue> listTags(ImageReference image) {
            catalogues++;
            return result;
        }

        @Override
        public RegistryLookup<ManifestMetadata> readManifest(ImageReference image, String reference) {
            manifests++;
            return reference.equals("latest")
                    ? new RegistryLookup.Available<>(new ManifestMetadata(remote, ManifestMetadata.Kind.INDEX))
                    : new RegistryLookup.Missing<>();
        }
    }
}
