package com.roomelephant.porthole.core.port.outbound;

import com.roomelephant.porthole.core.model.*;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface RegistryOperations {
    RegistryLookup<TagCatalogue> listTags(ImageReference image);

    ManifestEvidence readManifests(ImageReference image, List<String> installedDigests);

    void invalidateCache();
}
