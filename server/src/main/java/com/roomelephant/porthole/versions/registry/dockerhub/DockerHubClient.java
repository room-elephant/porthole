package com.roomelephant.porthole.versions.registry.dockerhub;

import com.roomelephant.porthole.shared.ImageReference;
import com.roomelephant.porthole.versions.*;
import com.roomelephant.porthole.versions.registry.*;
import com.roomelephant.porthole.versions.registry.RegistryProperties;
import com.roomelephant.porthole.versions.registry.distribution.DistributionClient;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;

/**
 * Reads Docker Hub through its registry API. The hub.docker.com tag API refuses anonymous clients after 1000 tags, so
 * repositories like nginx would never get a catalogue there.
 */
@NullMarked
public final class DockerHubClient implements RegistryClient {
    /** The most tags Docker Hub's registry returns per page. */
    static final int PAGE_SIZE = 1000;

    private final RegistryProperties.DockerHub properties;
    private final Duration readTimeout;
    private final DistributionClient distribution;

    public DockerHubClient(
            RegistryProperties.DockerHub properties, Duration readTimeout, DistributionClient distribution) {
        this.properties = properties;
        this.readTimeout = readTimeout;
        this.distribution = distribution;
    }

    @Override
    public boolean supports(ImageReference image) {
        return image.isMutable() && image.registry().equals("docker.io");
    }

    @Override
    public RegistryLookup<TagCatalogue> listTags(ImageReference image) {
        URI first = URI.create(properties.registryUrl() + image.repository() + "/tags/list?n=" + PAGE_SIZE);
        return RegistryRequests.read(
                "tags of docker.io/" + image.repository(),
                () -> TagPagination.read(first, readTimeout, page -> distribution.readTagPage(image, page)));
    }

    @Override
    public RegistryLookup<ManifestMetadata> readManifest(ImageReference image, String reference) {
        return distribution.readManifest(image, reference);
    }
}
