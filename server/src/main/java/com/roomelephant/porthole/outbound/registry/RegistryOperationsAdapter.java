package com.roomelephant.porthole.outbound.registry;

import com.github.benmanes.caffeine.cache.*;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.ImageReference;
import com.roomelephant.porthole.core.port.outbound.RegistryOperations;
import java.util.*;
import org.jspecify.annotations.*;

@NullMarked
public final class RegistryOperationsAdapter implements RegistryOperations {
    private final List<RegistryClient> clients;
    private final Cache<String, String> versionCache;

    public RegistryOperationsAdapter(List<RegistryClient> clients, RegistryProperties.Cache cache) {
        this.clients = List.copyOf(clients);
        this.versionCache = Caffeine.newBuilder()
                .expireAfterWrite(cache.ttl())
                .maximumSize(cache.versionMaxSize())
                .build();
    }

    @Override
    public @Nullable String getLatestVersion(String imageName) {
        Optional<ImageReference> image = ImageReference.tryParse(imageName).filter(ImageReference::supportsUpdates);
        if (image.isEmpty()) return null;
        RegistryClient client = clientFor(image.get());
        if (client == null) return null;
        return versionCache.get(image.get().canonicalRepository(), key -> client.getLatestVersion(image.get()));
    }

    @Override
    public boolean isTagChanged(String imageName, List<String> localDigests) {
        Optional<ImageReference> image = ImageReference.tryParse(imageName).filter(ImageReference::supportsUpdates);
        if (image.isEmpty() || localDigests.isEmpty()) return false;
        RegistryClient client = clientFor(image.get());
        return client != null && client.isTagChanged(image.get(), localDigests);
    }

    @Override
    public void invalidateVersionCache() {
        versionCache.invalidateAll();
    }

    private @Nullable RegistryClient clientFor(ImageReference image) {
        return clients.stream()
                .filter(client -> client.supports(image))
                .findFirst()
                .orElse(null);
    }
}
