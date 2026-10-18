package com.roomelephant.porthole.core.model;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ManifestEvidence(
        RegistryLookup<ManifestMetadata> tagged, Map<String, RegistryLookup<ManifestMetadata>> installed) {
    public ManifestEvidence {
        Objects.requireNonNull(tagged);
        installed = Map.copyOf(installed);
    }
}
