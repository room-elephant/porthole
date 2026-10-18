package com.roomelephant.porthole.core.version;

import static com.roomelephant.porthole.core.version.UpdatePolicy.Decision.NO_UPDATE;
import static com.roomelephant.porthole.core.version.UpdatePolicy.Decision.UPDATE_AVAILABLE;
import static com.roomelephant.porthole.core.version.UpdatePolicy.Evidence.*;
import static org.junit.jupiter.api.Assertions.*;

import com.roomelephant.porthole.core.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class UpdatePolicyTest {
    private final UpdatePolicy policy = new UpdatePolicy();
    private static final String A = "sha256:" + "a".repeat(64);
    private static final String B = "sha256:" + "b".repeat(64);

    @Test
    void comparisonsDistinguishChangesFromMissingEvidence() {
        var remote = new RegistryLookup.Available<>(new ManifestMetadata(B, ManifestMetadata.Kind.INDEX));
        assertEquals(
                POSITIVE,
                policy.manifest(
                        List.of(A),
                        new ManifestEvidence(
                                remote,
                                Map.of(
                                        A,
                                        new RegistryLookup.Available<>(
                                                new ManifestMetadata(A, ManifestMetadata.Kind.INDEX))))));
        assertEquals(
                INCONCLUSIVE,
                policy.manifest(List.of(A), new ManifestEvidence(remote, Map.of(A, new RegistryLookup.Missing<>()))));
        assertEquals(
                INCONCLUSIVE,
                policy.manifest(
                        List.of(A),
                        new ManifestEvidence(
                                remote,
                                Map.of(
                                        A,
                                        new RegistryLookup.Available<>(
                                                new ManifestMetadata(A, ManifestMetadata.Kind.IMAGE))))));
        assertEquals(NEGATIVE, policy.manifest(List.of(B), new ManifestEvidence(remote, Map.of())));
        assertEquals(
                INCONCLUSIVE,
                policy.manifest(
                        List.of(A),
                        new ManifestEvidence(
                                new RegistryLookup.Available<>(new ManifestMetadata(B, ManifestMetadata.Kind.UNKNOWN)),
                                Map.of())));
    }

    @Test
    void independentPositiveEvidenceWins() {
        assertEquals(UPDATE_AVAILABLE, policy.combine(POSITIVE, INCONCLUSIVE));
        assertEquals(UPDATE_AVAILABLE, policy.combine(INCONCLUSIVE, POSITIVE));
        assertEquals(UpdatePolicy.Decision.INCONCLUSIVE, policy.combine(NEGATIVE, INCONCLUSIVE));
        assertEquals(NO_UPDATE, policy.combine(INAPPLICABLE, NEGATIVE));
    }

    @Test
    void releasePolicyPreservesNumericEligibility() {
        var catalogue = new RegistryLookup.Available<>(new TagCatalogue(List.of("2.0")));
        assertEquals(
                POSITIVE, policy.release(ImageReference.tryParse("nginx:1.0").orElseThrow(), "1.0", catalogue));
        assertEquals(
                NEGATIVE, policy.release(ImageReference.tryParse("nginx:1.0").orElseThrow(), "2.0", catalogue));
        assertEquals(
                INAPPLICABLE,
                policy.release(ImageReference.tryParse("nginx:latest").orElseThrow(), "1.0", catalogue));
        assertEquals(
                INCONCLUSIVE,
                policy.release(
                        ImageReference.tryParse("nginx:1.0").orElseThrow(), "1.0", new RegistryLookup.Missing<>()));
    }
}
