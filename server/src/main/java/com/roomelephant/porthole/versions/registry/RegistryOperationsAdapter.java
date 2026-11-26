package com.roomelephant.porthole.versions.registry;

import com.github.benmanes.caffeine.cache.*;
import com.roomelephant.porthole.shared.ImageReference;
import com.roomelephant.porthole.versions.*;
import com.roomelephant.porthole.versions.RegistryOperations;
import java.time.Duration;
import java.util.*;
import org.jspecify.annotations.*;

@NullMarked
public final class RegistryOperationsAdapter implements RegistryOperations {
    private static final int MANIFEST_ENTRIES = 1_000;

    private final List<RegistryClient> clients;
    /**
     * How long a failed catalogue and manifest reads are shared. Long enough that containers checked in one run
     * (often replicas of one image) share a lookup; short enough that a failure is retried on the next run.
     */
    static final Duration SHARED_LOOKUP = Duration.ofMinutes(1);

    private final Cache<String, RegistryLookup<TagCatalogue>> catalogues;
    private final Cache<ManifestKey, RegistryLookup<ManifestMetadata>> manifests;

    public RegistryOperationsAdapter(List<RegistryClient> clients, RegistryProperties.Cache cache) {
        this(clients, cache, Ticker.systemTicker());
    }

    RegistryOperationsAdapter(List<RegistryClient> clients, RegistryProperties.Cache cache, Ticker ticker) {
        this.clients = List.copyOf(clients);
        long complete = cache.ttl().toNanos();
        long shared = Math.min(complete, SHARED_LOOKUP.toNanos());
        this.catalogues = Caffeine.newBuilder()
                .ticker(ticker)
                .maximumSize(cache.versionMaxSize())
                .expireAfter(Expiry.<String, RegistryLookup<TagCatalogue>>writing(
                        (_, lookup) -> lookup instanceof RegistryLookup.Available<TagCatalogue>
                                ? Duration.ofNanos(complete)
                                : Duration.ofNanos(shared)))
                .build();
        this.manifests = Caffeine.newBuilder()
                .ticker(ticker)
                .maximumSize(MANIFEST_ENTRIES)
                .expireAfterWrite(Duration.ofNanos(shared))
                .build();
    }

    // A complete catalogue is kept for the cache TTL; a failed one only briefly, so concurrent checks of one
    // repository wait for a single fetch instead of each repeating it.
    @Override
    public RegistryLookup<TagCatalogue> listTags(ImageReference image) {
        RegistryClient client = clientFor(image);
        if (client == null) return new RegistryLookup.Unsupported<>();
        return catalogues.get(image.canonicalRepository(), _ -> client.listTags(image));
    }

    @Override
    public ManifestEvidence readManifests(ImageReference image, List<String> installedDigests) {
        RegistryClient client = clientFor(image);
        if (client == null) return new ManifestEvidence(new RegistryLookup.Unsupported<>(), Map.of());
        if (installedDigests.isEmpty())
            return new ManifestEvidence(new RegistryLookup.Unavailable<>("No installed repository digest"), Map.of());
        RegistryLookup<ManifestMetadata> tagged =
                readManifest(client, image, Objects.requireNonNull(image.lookupTag()));
        if (!(tagged instanceof RegistryLookup.Available<ManifestMetadata>(ManifestMetadata remote))
                || remote.kind() == ManifestMetadata.Kind.UNKNOWN
                || installedDigests.contains(remote.digest())) return new ManifestEvidence(tagged, Map.of());
        Map<String, RegistryLookup<ManifestMetadata>> installed = new LinkedHashMap<>();
        String algorithm = remote.digest().split(":", 2)[0];
        for (String digest : installedDigests) {
            if (ImageReference.tryParse("image@" + digest).isPresent() && digest.split(":", 2)[0].equals(algorithm))
                installed.put(digest, readManifest(client, image, digest));
        }
        return new ManifestEvidence(tagged, installed);
    }

    private RegistryLookup<ManifestMetadata> readManifest(
            RegistryClient client, ImageReference image, String reference) {
        return manifests.get(
                new ManifestKey(image.canonicalRepository(), reference), _ -> client.readManifest(image, reference));
    }

    @Override
    public void invalidateCache() {
        catalogues.invalidateAll();
        manifests.invalidateAll();
    }

    private record ManifestKey(String repository, String reference) {}

    private @Nullable RegistryClient clientFor(ImageReference image) {
        if (!image.isMutable()) return null;
        List<RegistryClient> matching =
                clients.stream().filter(client -> client.supports(image)).toList();
        if (matching.size() > 1)
            throw new IllegalStateException("Multiple registry providers handle " + image.registry());
        return matching.isEmpty() ? null : matching.getFirst();
    }
}
