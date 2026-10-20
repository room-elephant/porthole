package com.roomelephant.porthole.outbound.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RegistryManifestTest {
    private static final String A = "sha256:" + "a".repeat(64);
    private static final String B = "sha256:" + "b".repeat(64);
    private static final String BASE = "https://registry.example/";

    private MockRestServiceServer server;
    private RegistryOperationsAdapter service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var props = new RegistryProperties(
                new RegistryProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(10)),
                new RegistryProperties.Cache(Duration.ofMinutes(1), 100),
                new RegistryProperties.DockerHub(BASE + "v2/", BASE + "auth?scope=repository:", BASE + "catalog/"),
                ghcr());
        service = RegistryTestClients.operations(builder.build(), props);
        server.expect(requestTo(BASE + "auth?scope=repository:library/nginx:pull"))
                .andRespond(withSuccess("{\"token\":\"test\"}", MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest
    @CsvSource({
        "application/vnd.oci.image.index.v1+json,application/vnd.oci.image.index.v1+json,true",
        "application/vnd.docker.distribution.manifest.list.v2+json,application/vnd.oci.image.index.v1+json,true",
        "application/vnd.oci.image.manifest.v1+json,application/vnd.docker.distribution.manifest.v2+json,true",
        "application/vnd.oci.image.index.v1+json,application/vnd.oci.image.manifest.v1+json,false",
        "application/vnd.oci.image.manifest.v1+json,application/vnd.oci.image.index.v1+json,false"
    })
    void onlyComparesDigestsAtTheSameManifestLevel(String remoteType, String localType, boolean expected) {
        server.expect(requestTo(BASE + "v2/library/nginx/manifests/latest"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(
                        withSuccess("", MediaType.parseMediaType(remoteType)).header("Docker-Content-Digest", B));
        server.expect(requestTo(BASE + "v2/library/nginx/manifests/" + A))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withSuccess("", MediaType.parseMediaType(localType)).header("Docker-Content-Digest", A));
        assertEquals(expected, changed("nginx:latest", List.of(A)));
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
        "same,application/vnd.oci.image.index.v1+json",
        "invalid,application/vnd.oci.image.index.v1+json",
        "different,text/plain"
    })
    void matchingOrUnavailableEvidenceCannotReportAChange(String digestKind, String type) {
        String digest = digestKind.equals("same") ? A : digestKind.equals("invalid") ? "sha256:bad" : B;
        server.expect(requestTo(BASE + "v2/library/nginx/manifests/latest"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withSuccess("", MediaType.parseMediaType(type)).header("Docker-Content-Digest", digest));
        assertFalse(changed("nginx:latest", List.of(A)));
        server.verify();
    }

    @org.junit.jupiter.api.Test
    void missingLocalManifestDoesNotProveAChange() {
        server.expect(requestTo(BASE + "v2/library/nginx/manifests/latest"))
                .andRespond(withSuccess("", MediaType.parseMediaType("application/vnd.oci.image.index.v1+json"))
                        .header("Docker-Content-Digest", B));
        server.expect(requestTo(BASE + "v2/library/nginx/manifests/" + A)).andRespond(withResourceNotFound());
        assertFalse(changed("nginx:latest", List.of(A)));
        server.verify();
    }

    @org.junit.jupiter.api.Test
    void missingLocalManifestDoesNotDiscardOtherComparableDigests() {
        String available = "sha256:" + "c".repeat(64);
        var type = MediaType.parseMediaType("application/vnd.oci.image.index.v1+json");
        server.expect(requestTo(BASE + "v2/library/nginx/manifests/latest"))
                .andRespond(withSuccess("", type).header("Docker-Content-Digest", B));
        server.expect(requestTo(BASE + "v2/library/nginx/manifests/" + A)).andRespond(withResourceNotFound());
        server.expect(requestTo(BASE + "v2/library/nginx/manifests/" + available))
                .andRespond(withSuccess("", type).header("Docker-Content-Digest", available));
        assertTrue(changed("nginx:latest", List.of(A, available)));
        server.verify();
    }

    @org.junit.jupiter.api.Test
    void differentDigestAlgorithmsDoNotProveDifferentContent() {
        String remote = "sha512:" + "b".repeat(128);
        var type = MediaType.parseMediaType("application/vnd.oci.image.index.v1+json");
        server.expect(requestTo(BASE + "v2/library/nginx/manifests/latest"))
                .andRespond(withSuccess("", type).header("Docker-Content-Digest", remote));
        assertFalse(changed("nginx:latest", List.of(A)));
        server.verify();
    }

    @org.junit.jupiter.api.Test
    void registryOutageIsNotACompletedNoUpdateCheck() {
        server.expect(requestTo(BASE + "v2/library/nginx/manifests/latest")).andRespond(withServerError());
        var digests = List.of(A);
        assertInstanceOf(
                com.roomelephant.porthole.core.model.RegistryLookup.Unavailable.class,
                service.readManifests(
                                com.roomelephant.porthole.core.model.ImageReference.tryParse("nginx:latest")
                                        .orElseThrow(),
                                digests)
                        .tagged());
        server.verify();
    }

    @org.junit.jupiter.api.Test
    void missingAuthenticationEndpointIsAFailedCheckRatherThanMissingManifest() {
        server.reset();
        server.expect(requestTo(BASE + "auth?scope=repository:library/nginx:pull"))
                .andRespond(withResourceNotFound());
        var digests = List.of(A);
        assertInstanceOf(
                com.roomelephant.porthole.core.model.RegistryLookup.Unavailable.class,
                service.readManifests(
                                com.roomelephant.porthole.core.model.ImageReference.tryParse("nginx:latest")
                                        .orElseThrow(),
                                digests)
                        .tagged());
        server.verify();
    }

    private boolean changed(String image, List<String> digests) {
        return new com.roomelephant.porthole.core.enrichment.version.resolution.UpdatePolicy()
                        .manifest(
                                digests,
                                service.readManifests(
                                        com.roomelephant.porthole.core.model.ImageReference.tryParse(image)
                                                .orElseThrow(),
                                        digests))
                == com.roomelephant.porthole.core.enrichment.version.resolution.UpdatePolicy.Evidence.POSITIVE;
    }

    private static RegistryProperties.Ghcr ghcr() {
        return new RegistryProperties.Ghcr(
                "https://ghcr.io/v2/", "https://ghcr.io/token?service=ghcr.io&scope=repository:");
    }
}
