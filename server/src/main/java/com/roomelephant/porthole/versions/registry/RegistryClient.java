package com.roomelephant.porthole.versions.registry;

import com.roomelephant.porthole.shared.ImageReference;
import com.roomelephant.porthole.versions.*;
import org.jspecify.annotations.NullMarked;

/** Provider contract internal to the registry adapter. */
@NullMarked
public interface RegistryClient {
    boolean supports(ImageReference image);

    RegistryLookup<TagCatalogue> listTags(ImageReference image);

    RegistryLookup<ManifestMetadata> readManifest(ImageReference image, String reference);
}
