package com.roomelephant.porthole.versions;

import com.roomelephant.porthole.shared.ImageReference;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface RegistryOperations {
    RegistryLookup<TagCatalogue> listTags(ImageReference image);

    ManifestEvidence readManifests(ImageReference image, List<String> installedDigests);

    void invalidateCache();
}
