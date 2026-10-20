package com.roomelephant.porthole.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RegistryLookupTest {
    @Test
    void successfulEvidenceCannotBeNullOrMutable() {
        assertThrows(NullPointerException.class, () -> new RegistryLookup.Available<>(null));
        var tags = new ArrayList<>(List.of("1.0"));
        var catalogue = new TagCatalogue(tags);
        tags.clear();
        assertEquals(List.of("1.0"), catalogue.tags());
        assertThrows(
                IllegalArgumentException.class, () -> new ManifestMetadata("sha256:bad", ManifestMetadata.Kind.IMAGE));
    }
}
