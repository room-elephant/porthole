package com.roomelephant.porthole.outbound.registry;

import com.roomelephant.porthole.core.model.*;
import org.jspecify.annotations.NullMarked;

/** Provider contract internal to the registry adapter. */
@NullMarked
public interface RegistryClient {
    boolean supports(ImageReference image);

    RegistryLookup<TagCatalogue> listTags(ImageReference image);

    RegistryLookup<ManifestMetadata> readManifest(ImageReference image, String reference);
}
