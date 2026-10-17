package com.roomelephant.porthole.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImageReferenceTest {
    private static final String DIGEST = "sha256:" + "a".repeat(64);

    @ParameterizedTest
    @CsvSource({
        "nginx,docker.io/library/nginx,latest,true",
        "a_b,docker.io/library/a_b,latest,true",
        "a__b,docker.io/library/a__b,latest,true",
        "a---b,docker.io/library/a---b,latest,true",
        "docker.io/nginx:1.25,docker.io/library/nginx,1.25,true",
        "index.docker.io/nginx,docker.io/library/nginx,latest,true",
        "registry-1.docker.io/library/nginx,docker.io/library/nginx,latest,true",
        "bitnami/redis:7,docker.io/bitnami/redis,7,true",
        "ghcr.io/acme/app:Release,ghcr.io/acme/app,Release,true",
        "ghcr.io/app,ghcr.io/app,latest,false",
        "ghcr.io:443/acme/app,ghcr.io:443/acme/app,latest,false",
        "localhost:5000/app,localhost:5000/app,latest,false",
        "[::1]:5000/app,[::1]:5000/app,latest,false",
        "MYREGISTRY/app:Release,myregistry/app,Release,false",
        "docker.io:443/app,docker.io:443/app,latest,false"
    })
    void preservesIdentity(String value, String repository, String tag, boolean supported) {
        var reference = ImageReference.tryParse(value).orElseThrow();
        assertEquals(repository, reference.canonicalRepository());
        assertEquals(tag, reference.lookupTag());
        assertEquals(supported, reference.supportsUpdates());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nginx@", "nginx:1.25@"})
    void recognizesPins(String prefix) {
        var reference = ImageReference.tryParse(prefix + DIGEST).orElseThrow();
        assertEquals("nginx", reference.leafName());
        assertEquals(DIGEST, reference.digest());
        assertNull(reference.lookupTag());
        assertFalse(reference.supportsUpdates());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                "https://docker.io/nginx",
                "nginx?foo",
                "nginx#foo",
                "nginx:",
                "nginx@bad",
                "nginx@@sha256:abc",
                "user/UPPER",
                "host:bad/app",
                "user//app",
                "a___b",
                "a_",
                "a-",
                "a.",
                "user/app/"
            })
    void rejectsMalformedReferences(String value) {
        assertTrue(ImageReference.tryParse(value).isEmpty());
    }

    @Test
    void rejectsImageIdsAndScopesDigestsToTheRepository() {
        assertTrue(ImageReference.tryParse(DIGEST).isEmpty());
        assertTrue(ImageReference.tryParse("a".repeat(64)).isEmpty());
        assertTrue(ImageReference.tryParse(null).isEmpty());
        var reference = ImageReference.tryParse("nginx:1.25").orElseThrow();
        assertEquals(
                List.of(DIGEST),
                reference.matchingDigests(List.of(
                        "ghcr.io/other/nginx@" + DIGEST, "index.docker.io/library/nginx@" + DIGEST, "nginx@bad")));
    }
}
