package com.roomelephant.porthole.versions.registry.ghcr;

import com.roomelephant.porthole.shared.ImageReference;
import com.roomelephant.porthole.versions.*;
import com.roomelephant.porthole.versions.registry.*;
import com.roomelephant.porthole.versions.registry.RegistryProperties;
import com.roomelephant.porthole.versions.registry.distribution.DistributionClient;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class GhcrClient implements RegistryClient {
    private final RegistryProperties.Ghcr properties;
    private final Duration readTimeout;
    private final DistributionClient distribution;

    public GhcrClient(RegistryProperties.Ghcr properties, Duration readTimeout, DistributionClient distribution) {
        this.properties = properties;
        this.readTimeout = readTimeout;
        this.distribution = distribution;
    }

    @Override
    public boolean supports(ImageReference image) {
        return image.isMutable()
                && image.registry().equals("ghcr.io")
                && image.repository().contains("/");
    }

    @Override
    public RegistryLookup<TagCatalogue> listTags(ImageReference image) {
        URI first = URI.create(properties.registryUrl() + image.repository() + "/tags/list?n=100");
        return RegistryRequests.read(
                "tags of ghcr.io/" + image.repository(),
                () -> TagPagination.read(first, readTimeout, page -> distribution.readTagPage(image, page)));
    }

    @Override
    public RegistryLookup<ManifestMetadata> readManifest(ImageReference image, String reference) {
        return distribution.readManifest(image, reference);
    }
}
