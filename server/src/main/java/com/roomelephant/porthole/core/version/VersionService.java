package com.roomelephant.porthole.core.version;

import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.outbound.*;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

@Service
@NullMarked
public class VersionService {
    private final DockerOperations docker;
    private final RegistryOperations registry;
    private final InstalledVersionDetector detector = new InstalledVersionDetector();
    private final UpdatePolicy policy = new UpdatePolicy();

    public VersionService(DockerOperations docker, RegistryOperations registry) {
        this.docker = docker;
        this.registry = registry;
    }

    public void invalidateRegistryCache() {
        registry.invalidateVersionCache();
    }

    public VersionInfo getVersionInfo(ContainerKey key) {
        ContainerInspection inspection = docker.inspectContainer(key);
        String image = inspection.image();
        if (image == null) return new VersionInfo(null, null, false);
        String current = detector.detect(inspection);
        var reference = ImageReference.tryParse(image).filter(ImageReference::supportsUpdates);
        if (reference.isEmpty()) return new VersionInfo(current, null, false);
        var repoDigests = docker.getRepoDigests(key.sourceId(), inspection.imageId());
        var matching = repoDigests == null ? List.<String>of() : reference.get().matchingDigests(repoDigests);
        if (matching.isEmpty()) return new VersionInfo(current, null, false);
        String latest = registry.getLatestVersion(image);
        boolean changed = registry.isTagChanged(image, matching);
        return new VersionInfo(current, latest, policy.updateAvailable(image, current, latest, changed));
    }
}
