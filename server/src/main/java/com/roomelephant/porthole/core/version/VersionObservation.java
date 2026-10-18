package com.roomelephant.porthole.core.version;

import com.roomelephant.porthole.core.model.*;
import java.util.*;
import org.jspecify.annotations.*;

/** Retained remote facts scoped to one installed image identity. */
@NullMarked
record VersionObservation(
        @Nullable String image,
        @Nullable String imageId,
        @Nullable String currentVersion,
        List<String> installedDigests,
        @Nullable TagCatalogue catalogue,
        @Nullable ManifestEvidence manifests) {
    VersionObservation {
        installedDigests = List.copyOf(installedDigests);
    }

    boolean belongsTo(@Nullable String reference, @Nullable String id) {
        return id != null && !id.isBlank() && id.equals(imageId) && Objects.equals(reference, image);
    }

    VersionInfo toVersionInfo() {
        Optional<ImageReference> reference = ImageReference.tryParse(image);
        String latest = catalogue == null ? null : new ReleasePolicy().latest(catalogue);
        if (reference.isEmpty() || !reference.get().isMutable() || installedDigests.isEmpty())
            return new VersionInfo(currentVersion, null, false);
        UpdatePolicy policy = new UpdatePolicy();
        RegistryLookup<TagCatalogue> releases = catalogue == null
                ? new RegistryLookup.Unavailable<>("No catalogue evidence")
                : new RegistryLookup.Available<>(catalogue);
        UpdatePolicy.Evidence manifest =
                manifests == null ? UpdatePolicy.Evidence.INCONCLUSIVE : policy.manifest(installedDigests, manifests);
        return new VersionInfo(
                currentVersion,
                latest,
                policy.combine(policy.release(reference.get(), currentVersion, releases), manifest)
                        == UpdatePolicy.Decision.UPDATE_AVAILABLE);
    }
}
