package com.roomelephant.porthole.core.version;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.outbound.*;
import java.util.*;
import org.junit.jupiter.api.*;

class VersionObservationTest {
    private static final ContainerKey KEY = new ContainerKey("local", "app");
    private static final ImageReference IMAGE =
            ImageReference.tryParse("nginx:1.0").orElseThrow();
    private static final String A = "sha256:" + "a".repeat(64), B = "sha256:" + "b".repeat(64);
    private final DockerOperations docker = mock(DockerOperations.class);
    private final RegistryOperations registry = mock(RegistryOperations.class);
    private final VersionService service = new VersionService(docker, registry);

    @BeforeEach
    void setUp() {
        when(docker.inspectContainer(KEY)).thenReturn(new ContainerInspection("nginx:1.0", A, List.of(), Map.of()));
        when(docker.getRepoDigests("local", A)).thenReturn(List.of("nginx@" + A));
    }

    private ManifestEvidence manifest(boolean changed) {
        return new ManifestEvidence(
                new RegistryLookup.Available<>(new ManifestMetadata(changed ? B : A, ManifestMetadata.Kind.INDEX)),
                Map.of(A, new RegistryLookup.Available<>(new ManifestMetadata(A, ManifestMetadata.Kind.INDEX))));
    }

    private ManifestEvidence unavailable() {
        return new ManifestEvidence(new RegistryLookup.Unavailable<>("offline"), Map.of());
    }

    @Test
    void catalogueFailureDoesNotBlockChangedImage() {
        when(registry.listTags(IMAGE)).thenReturn(new RegistryLookup.Unavailable<>("offline"));
        when(registry.readManifests(IMAGE, List.of(A))).thenReturn(manifest(true));
        assertTrue(service.resolve(KEY, null).toVersionInfo().updateAvailable());
    }

    @Test
    void manifestFailureDoesNotDiscardNewerRelease() {
        when(registry.listTags(IMAGE)).thenReturn(new RegistryLookup.Available<>(new TagCatalogue(List.of("2.0"))));
        when(registry.readManifests(IMAGE, List.of(A))).thenReturn(unavailable());
        assertEquals(
                new VersionInfo("1.0", "2.0", true), service.resolve(KEY, null).toVersionInfo());
    }

    @Test
    void failuresRetainEachChannelAndSuccessfulNegativesClearIt() {
        when(registry.listTags(IMAGE))
                .thenReturn(
                        new RegistryLookup.Available<>(new TagCatalogue(List.of("2.0"))),
                        new RegistryLookup.Unavailable<>("offline"),
                        new RegistryLookup.Available<>(new TagCatalogue(List.of("1.0"))));
        when(registry.readManifests(IMAGE, List.of(A))).thenReturn(manifest(true), manifest(false), unavailable());
        var first = service.resolve(KEY, null);
        var second = service.resolve(KEY, first);
        var third = service.resolve(KEY, second);
        assertTrue(second.toVersionInfo().updateAvailable());
        assertEquals(new VersionInfo("1.0", "1.0", false), third.toVersionInfo());
    }

    @Test
    void manifestPositiveSurvivesFailureWhileReleaseBecomesNegative() {
        when(registry.listTags(IMAGE))
                .thenReturn(
                        new RegistryLookup.Available<>(new TagCatalogue(List.of("2.0"))),
                        new RegistryLookup.Available<>(new TagCatalogue(List.of("1.0"))));
        when(registry.readManifests(IMAGE, List.of(A))).thenReturn(manifest(true), unavailable());
        var first = service.resolve(KEY, null);
        assertTrue(service.resolve(KEY, first).toVersionInfo().updateAvailable());
    }

    @Test
    void installedImageChangeDiscardsPreviousEvidence() {
        when(registry.listTags(IMAGE))
                .thenReturn(
                        new RegistryLookup.Available<>(new TagCatalogue(List.of("2.0"))),
                        new RegistryLookup.Unavailable<>("offline"));
        when(registry.readManifests(IMAGE, List.of(A))).thenReturn(manifest(true), unavailable());
        var first = service.resolve(KEY, null);
        when(docker.inspectContainer(KEY)).thenReturn(new ContainerInspection("nginx:1.0", B, List.of(), Map.of()));
        when(docker.getRepoDigests("local", B)).thenReturn(List.of("nginx@" + A));
        assertEquals(
                new VersionInfo("1.0", null, false), service.resolve(KEY, first).toVersionInfo());
    }

    @Test
    void missingIdentityCannotCarryEvidence() {
        when(registry.listTags(IMAGE))
                .thenReturn(
                        new RegistryLookup.Available<>(new TagCatalogue(List.of("2.0"))),
                        new RegistryLookup.Unavailable<>("offline"));
        when(registry.readManifests(IMAGE, List.of(A))).thenReturn(manifest(true), unavailable());
        var first = service.resolve(KEY, null);
        when(docker.inspectContainer(KEY)).thenReturn(new ContainerInspection("nginx:1.0", null, List.of(), Map.of()));
        when(docker.getRepoDigests("local", null)).thenReturn(List.of("nginx@" + A));
        assertFalse(service.resolve(KEY, first).toVersionInfo().updateAvailable());
    }

    @Test
    void malformedManifestHeaderDoesNotDiscardSuccessfulCatalogue() {
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder)
                .build();
        var http = builder.build();
        var config = new com.roomelephant.porthole.config.RegistryConfig();
        var props = new com.roomelephant.porthole.config.properties.RegistryProperties(
                new com.roomelephant.porthole.config.properties.RegistryProperties.Timeout(
                        java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(10)),
                new com.roomelephant.porthole.config.properties.RegistryProperties.Cache(
                        java.time.Duration.ofMinutes(1), 100),
                new com.roomelephant.porthole.config.properties.RegistryProperties.DockerHub(
                        "https://registry/", "https://auth/", "https://catalogue/"),
                new com.roomelephant.porthole.config.properties.RegistryProperties.Ghcr(
                        "https://ghcr/", "https://ghcr-auth/"));
        var adapter = new com.roomelephant.porthole.outbound.registry.RegistryOperationsAdapter(
                List.of(config.dockerHubClient(http, new com.fasterxml.jackson.databind.ObjectMapper(), props)),
                props.cache());
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "https://catalogue/library/nginx/tags?page_size=100"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"results\":[{\"name\":\"2.0\"}]}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "https://auth/library/nginx:pull"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"token\":\"test\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "https://registry/library/nginx/manifests/1.0"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess()
                        .header("Content-Type", "bad-type")
                        .header("Docker-Content-Digest", A));
        assertEquals(
                new VersionInfo("1.0", "2.0", true),
                new VersionService(docker, adapter).resolve(KEY, null).toVersionInfo());
        server.verify();
    }
}
