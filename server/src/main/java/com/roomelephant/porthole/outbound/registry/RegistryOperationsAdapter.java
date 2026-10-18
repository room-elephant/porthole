package com.roomelephant.porthole.outbound.registry;

import com.github.benmanes.caffeine.cache.*;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.outbound.RegistryOperations;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.*;

@NullMarked
public final class RegistryOperationsAdapter implements RegistryOperations {
    private final List<RegistryClient> clients;
    private final Cache<String, @Nullable TagCatalogue> catalogues;

    public RegistryOperationsAdapter(List<RegistryClient> clients, RegistryProperties.Cache cache) {
        this.clients = List.copyOf(clients);
        this.catalogues = Caffeine.newBuilder()
                .expireAfterWrite(cache.ttl())
                .maximumSize(cache.versionMaxSize())
                .build();
    }

    @Override
    public RegistryLookup<TagCatalogue> listTags(ImageReference image) {
        RegistryClient client = clientFor(image);
        if (client == null) return new RegistryLookup.Unsupported<>();
        AtomicReference<RegistryLookup<TagCatalogue>> fetched = new AtomicReference<>();
        @Nullable
        TagCatalogue catalogue = catalogues.get(image.canonicalRepository(), key -> {
            RegistryLookup<TagCatalogue> result = client.listTags(image);
            fetched.set(result);
            return result instanceof RegistryLookup.Available<TagCatalogue>(TagCatalogue tags) ? tags : null;
        });
        return catalogue == null ? Objects.requireNonNull(fetched.get()) : new RegistryLookup.Available<>(catalogue);
    }

    @Override
    public ManifestEvidence readManifests(ImageReference image, List<String> installedDigests) {
        RegistryClient client = clientFor(image);
        if (client == null) return new ManifestEvidence(new RegistryLookup.Unsupported<>(), Map.of());
        if (installedDigests.isEmpty())
            return new ManifestEvidence(new RegistryLookup.Unavailable<>("No installed repository digest"), Map.of());
        RegistryLookup<ManifestMetadata> tagged = client.readManifest(image, Objects.requireNonNull(image.lookupTag()));
        if (!(tagged instanceof RegistryLookup.Available<ManifestMetadata>(ManifestMetadata remote))
                || remote.kind() == ManifestMetadata.Kind.UNKNOWN
                || installedDigests.contains(remote.digest())) return new ManifestEvidence(tagged, Map.of());
        Map<String, RegistryLookup<ManifestMetadata>> installed = new LinkedHashMap<>();
        String algorithm = remote.digest().split(":", 2)[0];
        for (String digest : installedDigests) {
            if (ImageReference.tryParse("image@" + digest).isPresent() && digest.split(":", 2)[0].equals(algorithm))
                installed.put(digest, client.readManifest(image, digest));
        }
        return new ManifestEvidence(tagged, installed);
    }

    @Override
    public void invalidateCache() {
        catalogues.invalidateAll();
    }

    private @Nullable RegistryClient clientFor(ImageReference image) {
        if (!image.isMutable()) return null;
        List<RegistryClient> matching =
                clients.stream().filter(client -> client.supports(image)).toList();
        if (matching.size() > 1)
            throw new IllegalStateException("Multiple registry providers handle " + image.registry());
        return matching.isEmpty() ? null : matching.getFirst();
    }
}
