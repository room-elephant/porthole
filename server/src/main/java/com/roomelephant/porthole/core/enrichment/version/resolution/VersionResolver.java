package com.roomelephant.porthole.core.enrichment.version.resolution;

import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.outbound.*;
import java.util.*;
import org.jspecify.annotations.*;
import org.springframework.stereotype.Service;

@Service
@NullMarked
public class VersionResolver {
    private final DockerOperations docker;
    private final RegistryOperations registry;
    private final InstalledVersionDetector detector = new InstalledVersionDetector();
    private final UpdatePolicy policy = new UpdatePolicy();

    public VersionResolver(DockerOperations docker, RegistryOperations registry) {
        this.docker = docker;
        this.registry = registry;
    }

    public void invalidateRegistryCache() {
        registry.invalidateCache();
    }

    public VersionObservation resolve(ContainerKey key, @Nullable VersionObservation previous) {
        ContainerInspection inspection = docker.inspectContainer(key);
        String image = inspection.image();
        String current = detector.detect(inspection);
        Optional<ImageReference> reference = ImageReference.tryParse(image).filter(ImageReference::isMutable);
        if (reference.isEmpty())
            return new VersionObservation(image, inspection.imageId(), current, List.of(), null, null);
        List<String> repoDigests = docker.getRepoDigests(key.sourceId(), inspection.imageId());
        List<String> matching =
                repoDigests == null ? List.of() : reference.get().matchingDigests(repoDigests);
        if (matching.isEmpty())
            return new VersionObservation(image, inspection.imageId(), current, matching, null, null);
        TagCatalogue catalogue = null;
        ManifestEvidence conclusive = null;
        if (previous != null && previous.belongsTo(image, inspection.imageId())) {
            catalogue = previous.catalogue();
            conclusive = previous.manifests();
        }
        RegistryLookup<TagCatalogue> releases = registry.listTags(reference.get());
        ManifestEvidence manifest = registry.readManifests(reference.get(), matching);
        if (releases instanceof RegistryLookup.Available<TagCatalogue>(TagCatalogue freshCatalogue)) {
            catalogue = freshCatalogue;
        }
        UpdatePolicy.Evidence change = policy.manifest(matching, manifest);
        if (change == UpdatePolicy.Evidence.POSITIVE || change == UpdatePolicy.Evidence.NEGATIVE) {
            conclusive = manifest;
        }
        return new VersionObservation(image, inspection.imageId(), current, matching, catalogue, conclusive);
    }
}
