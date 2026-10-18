package com.roomelephant.porthole.core.version;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.ContainerConfig;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.model.exception.NotFoundException;
import com.roomelephant.porthole.core.model.exception.UnexpectedException;
import com.roomelephant.porthole.core.port.outbound.RegistryOperations;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("VersionService")
class VersionServiceTest {
    private static final String LOCAL_DIGEST = "sha256:" + "a".repeat(64);

    @Mock
    private DockerClient dockerClient;

    @Mock
    private RegistryOperations registryService;

    @Mock
    private InspectContainerCmd inspectContainerCmd;

    @Mock
    private InspectContainerResponse inspectContainerResponse;

    @Mock
    private InspectImageCmd inspectImageCmd;

    @Mock
    private InspectImageResponse inspectImageResponse;

    @Mock
    private ContainerConfig containerConfig;

    private VersionService versionService;

    private static ImageReference ref(String image) {
        return ImageReference.tryParse(image).orElseThrow();
    }

    private static RegistryLookup<TagCatalogue> catalogue(String latest) {
        return new RegistryLookup.Available<>(new TagCatalogue(latest == null ? List.of() : List.of(latest)));
    }

    private static ManifestEvidence evidence(boolean changed) {
        String remote = changed ? "sha256:" + "b".repeat(64) : LOCAL_DIGEST;
        return new ManifestEvidence(
                new RegistryLookup.Available<>(new ManifestMetadata(remote, ManifestMetadata.Kind.INDEX)),
                Map.of(
                        LOCAL_DIGEST,
                        new RegistryLookup.Available<>(
                                new ManifestMetadata(LOCAL_DIGEST, ManifestMetadata.Kind.INDEX))));
    }

    @BeforeEach
    void setUp() {
        versionService = new VersionService(
                com.roomelephant.porthole.DockerTestSources.operationsOf(dockerClient), registryService);
    }

    @Nested
    @DisplayName("getVersionInfo")
    class GetVersionInfo {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(
                strings = {
                    "ghcr.io/acme/app@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "nginx@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "nginx:1.25@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                })
        void pinnedOrInvalidImagesDoNotQueryRegistry(String image) {
            setupContainerWithImage(image);
            assertFalse(versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo()
                    .updateAvailable());
            verifyNoInteractions(registryService);
            verify(dockerClient, never()).inspectImageCmd(anyString());
        }

        @Test
        void mutableReferencesAreResolvedThroughTheRegistryPortWithoutAProviderAllowlist() {
            String image = "quay.io/acme/app:1.0";
            setupContainerWithImage(image);
            setupRemoteImage();
            when(inspectImageResponse.getRepoDigests()).thenReturn(List.of("quay.io/acme/app@" + LOCAL_DIGEST));
            when(registryService.listTags(ref(image))).thenReturn(catalogue("2.0"));
            when(registryService.readManifests(ref(image), List.of(LOCAL_DIGEST)))
                    .thenReturn(evidence(false));
            assertTrue(versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo()
                    .updateAvailable());
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({
            "2.0,1.0,false",
            "v1.0,1.0.0,false",
            "1.0.0,1.0,false",
            "release-build,2.0,false",
            "1.0,2.0,true",
            "2147483648.0,2.0,false"
        })
        void onlyNewerComparableVersionsTriggerWarnings(String current, String latest, boolean expected) {
            setupContainerWithImage("nginx:1.0");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"NGINX_VERSION=" + current});
            when(registryService.listTags(ref("nginx:1.0"))).thenReturn(catalogue(latest));
            when(registryService.readManifests(eq(ref("nginx:1.0")), anyList())).thenReturn(evidence(false));
            assertEquals(
                    expected,
                    versionService
                            .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                            .toVersionInfo()
                            .updateAvailable());
        }

        @Test
        void specificVersionWinsEvenWhenGenericAppearsFirst() {
            setupContainerWithImage("nginx:1.0");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"VERSION=9.9", "NGINX_VERSION=1.0"});
            when(registryService.listTags(ref("nginx:1.0"))).thenReturn(catalogue("1.0"));
            when(registryService.readManifests(eq(ref("nginx:1.0")), anyList())).thenReturn(evidence(false));
            assertEquals(
                    "1.0",
                    versionService
                            .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                            .toVersionInfo()
                            .currentVersion());
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({
            "ghcr.io/acme/app,true",
            "docker.io/acme/app,false",
            "ghcr.io/other/app,false"
        })
        void ghcrUpdatesRequireItsOwnRepositoryProvenance(String provenance, boolean eligible) {
            String image = "ghcr.io/acme/app:1.0";
            setupContainerWithImage(image);
            setupRemoteImage();
            when(inspectImageResponse.getRepoDigests()).thenReturn(List.of(provenance + "@" + LOCAL_DIGEST));
            if (eligible) {
                when(registryService.listTags(ref(image))).thenReturn(catalogue("2.0"));
                when(registryService.readManifests(ref(image), List.of(LOCAL_DIGEST)))
                        .thenReturn(evidence(false));
            }
            assertEquals(
                    eligible,
                    versionService
                            .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                            .toVersionInfo()
                            .updateAvailable());
            if (!eligible) verifyNoInteractions(registryService);
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({"'',2.0", "'   ',2.0", "' 3.0 ',3.0"})
        void blankMetadataFallsThroughAndNonblankMetadataIsTrimmed(String specific, String expected) {
            setupContainerWithImage("nginx:latest");
            when(inspectContainerResponse.getImageId()).thenReturn("sha256:local");
            when(dockerClient.inspectImageCmd("sha256:local")).thenReturn(inspectImageCmd);
            when(inspectImageCmd.exec()).thenReturn(inspectImageResponse);
            when(inspectImageResponse.getRepoDigests()).thenReturn(List.of());
            when(containerConfig.getEnv()).thenReturn(new String[] {"NGINX_VERSION=" + specific, "VERSION=  "});
            if (specific.isBlank()) {
                when(containerConfig.getLabels())
                        .thenReturn(Map.of("org.opencontainers.image.version", "  ", "version", " 2.0 "));
            }
            assertEquals(
                    expected,
                    versionService
                            .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                            .toVersionInfo()
                            .currentVersion());
        }

        @Test
        void pinnedReferenceRetainsExplicitTagForDisplay() {
            setupContainerWithImage("nginx:1.25@" + LOCAL_DIGEST);
            assertEquals(
                    "1.25",
                    versionService
                            .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                            .toVersionInfo()
                            .currentVersion());
            verifyNoInteractions(registryService);
        }

        @Test
        void unrelatedImageProvenanceDoesNotQueryRegistry() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(inspectImageResponse.getRepoDigests()).thenReturn(List.of("ghcr.io/acme/nginx@" + LOCAL_DIGEST));
            assertFalse(versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo()
                    .updateAvailable());
            verifyNoInteractions(registryService);
        }

        @Test
        @DisplayName("should throw NotFoundException when container not found")
        void shouldThrowNotFoundExceptionWhenContainerNotFound() {
            when(dockerClient.inspectContainerCmd("unknown")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec())
                    .thenThrow(new com.github.dockerjava.api.exception.NotFoundException("Not found"));

            var key = new com.roomelephant.porthole.core.model.ContainerKey("local", "unknown");
            NotFoundException exception = assertThrows(
                    NotFoundException.class,
                    () -> versionService.resolve(key, null).toVersionInfo());

            assertEquals("unknown", exception.getContainerId());
        }

        @Test
        @DisplayName("should throw UnexpectedException when docker fails")
        void shouldThrowUnexpectedExceptionWhenDockerFails() {
            when(dockerClient.inspectContainerCmd("container1")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec()).thenThrow(new RuntimeException("Docker error"));

            var key = new com.roomelephant.porthole.core.model.ContainerKey("local", "container1");
            assertThrows(
                    UnexpectedException.class,
                    () -> versionService.resolve(key, null).toVersionInfo());
        }

        @Test
        @DisplayName("should return empty VersionInfo when container config is null")
        void shouldReturnEmptyVersionInfoWhenContainerConfigIsNull() {
            when(dockerClient.inspectContainerCmd("container1")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
            when(inspectContainerResponse.getConfig()).thenReturn(null);

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertNull(result.currentVersion());
            assertNull(result.latestVersion());
            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should return empty VersionInfo when image is null")
        void shouldReturnEmptyVersionInfoWhenImageIsNull() {
            when(dockerClient.inspectContainerCmd("container1")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
            when(inspectContainerResponse.getConfig()).thenReturn(containerConfig);
            when(containerConfig.getImage()).thenReturn(null);

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertNull(result.currentVersion());
            assertNull(result.latestVersion());
            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should return version from tag for local image")
        void shouldReturnVersionFromTagForLocalImage() {
            setupContainerWithImage("nginx:1.25");
            when(inspectContainerResponse.getImageId()).thenReturn("sha256:abc123");
            when(dockerClient.inspectImageCmd("sha256:abc123")).thenReturn(inspectImageCmd);
            when(inspectImageCmd.exec()).thenReturn(inspectImageResponse);
            when(inspectImageResponse.getRepoDigests()).thenReturn(Collections.emptyList());
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertEquals("1.25", result.currentVersion());
            assertNull(result.latestVersion());
            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should return version from OCI label")
        void shouldReturnVersionFromOciLabel() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(Map.of("org.opencontainers.image.version", "1.25.0"));
            when(registryService.listTags(ref("nginx:latest"))).thenReturn(catalogue("1.26.0"));
            when(registryService.readManifests(eq(ref("nginx:latest")), anyList()))
                    .thenReturn(evidence(true));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertEquals("1.25.0", result.currentVersion());
            assertEquals("1.26.0", result.latestVersion());
        }

        @Test
        @DisplayName("should return version from version label")
        void shouldReturnVersionFromVersionLabel() {
            setupContainerWithImage("myapp:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(Map.of("version", "2.0.0"));
            when(registryService.listTags(ref("myapp:latest"))).thenReturn(catalogue("2.1.0"));
            when(registryService.readManifests(eq(ref("myapp:latest")), anyList()))
                    .thenReturn(evidence(true));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertEquals("2.0.0", result.currentVersion());
        }

        @Test
        @DisplayName("should return version from environment variable")
        void shouldReturnVersionFromEnvironmentVariable() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"NGINX_VERSION=1.25.0"});
            when(registryService.listTags(ref("nginx:latest"))).thenReturn(catalogue("1.26.0"));
            when(registryService.readManifests(eq(ref("nginx:latest")), anyList()))
                    .thenReturn(evidence(true));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertEquals("1.25.0", result.currentVersion());
        }

        @Test
        @DisplayName("should return version from generic VERSION env var")
        void shouldReturnVersionFromGenericVersionEnvVar() {
            setupContainerWithImage("myapp:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"VERSION=3.0.0"});
            when(registryService.listTags(ref("myapp:latest"))).thenReturn(catalogue("3.1.0"));
            when(registryService.readManifests(eq(ref("myapp:latest")), anyList()))
                    .thenReturn(evidence(true));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertEquals("3.0.0", result.currentVersion());
        }

        @Test
        @DisplayName("should detect update available when digest differs")
        void shouldDetectUpdateAvailableWhenDigestDiffers() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.listTags(ref("nginx:1.25"))).thenReturn(catalogue("1.26"));
            when(registryService.readManifests(eq(ref("nginx:1.25")), anyList()))
                    .thenReturn(evidence(true));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertTrue(result.updateAvailable());
        }

        @Test
        @DisplayName("should not detect update when digest matches")
        void shouldNotDetectUpdateWhenDigestMatches() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.listTags(ref("nginx:1.25"))).thenReturn(catalogue("1.25"));
            when(registryService.readManifests(eq(ref("nginx:1.25")), anyList()))
                    .thenReturn(evidence(false));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should detect update when semver versions differ")
        void shouldDetectUpdateWhenSemverVersionsDiffer() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(Map.of("org.opencontainers.image.version", "1.25.0"));
            when(registryService.listTags(ref("nginx:1.25"))).thenReturn(catalogue("1.26.0"));
            when(registryService.readManifests(eq(ref("nginx:1.25")), anyList()))
                    .thenReturn(evidence(false));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertTrue(result.updateAvailable());
        }

        @Test
        @DisplayName("should propagate registry service errors")
        void shouldPropagateRegistryOperationsErrors() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.listTags(ref("nginx:1.25"))).thenReturn(catalogue(null));
            when(registryService.readManifests(eq(ref("nginx:1.25")), anyList()))
                    .thenThrow(new RuntimeException("Network error"));

            var key = new com.roomelephant.porthole.core.model.ContainerKey("local", "container1");
            assertThrows(
                    RuntimeException.class,
                    () -> versionService.resolve(key, null).toVersionInfo());
        }

        @Test
        @DisplayName("should handle null repo digests")
        void shouldHandleNullRepoDigests() {
            setupContainerWithImage("nginx:1.25");
            when(inspectContainerResponse.getImageId()).thenReturn("sha256:abc123");
            when(dockerClient.inspectImageCmd("sha256:abc123")).thenReturn(inspectImageCmd);
            when(inspectImageCmd.exec()).thenReturn(inspectImageResponse);
            when(inspectImageResponse.getRepoDigests()).thenReturn(null);
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should propagate inspectImage failures")
        void shouldPropagateInspectImageFailure() {
            setupContainerWithImage("nginx:1.25");
            when(inspectContainerResponse.getImageId()).thenReturn("sha256:abc123");
            when(dockerClient.inspectImageCmd("sha256:abc123")).thenReturn(inspectImageCmd);
            when(inspectImageCmd.exec()).thenThrow(new RuntimeException("Image not found"));
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);

            var key = new com.roomelephant.porthole.core.model.ContainerKey("local", "container1");
            assertThrows(
                    RuntimeException.class,
                    () -> versionService.resolve(key, null).toVersionInfo());
        }

        @Test
        @DisplayName("should skip empty env var values")
        void shouldSkipEmptyEnvVarValues() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"NGINX_VERSION=", "VERSION="});
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.listTags(ref("nginx:latest"))).thenReturn(catalogue("1.26.0"));
            when(registryService.readManifests(eq(ref("nginx:latest")), anyList()))
                    .thenReturn(evidence(true));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertEquals("latest", result.currentVersion());
        }

        @Test
        @DisplayName("should not detect update without registry version metadata")
        void shouldNotDetectUpdateWithoutRegistryVersionMetadata() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.listTags(ref("nginx:1.25"))).thenReturn(catalogue(null));
            when(registryService.readManifests(eq(ref("nginx:1.25")), anyList()))
                    .thenReturn(evidence(false));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertFalse(result.updateAvailable());
            assertNull(result.latestVersion());
        }

        @Test
        @DisplayName("should not detect update for non-semver tag with null currentVersion")
        void shouldNotDetectUpdateForNonSemverTagWithNullCurrentVersion() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.listTags(ref("nginx:latest"))).thenReturn(catalogue("1.26.0"));
            when(registryService.readManifests(eq(ref("nginx:latest")), anyList()))
                    .thenReturn(evidence(false));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should not detect update when current and latest versions are equal")
        void shouldNotDetectUpdateWhenCurrentAndLatestVersionsAreEqual() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(Map.of("org.opencontainers.image.version", "1.26.0"));
            when(registryService.listTags(ref("nginx:1.25"))).thenReturn(catalogue("1.26.0"));
            when(registryService.readManifests(eq(ref("nginx:1.25")), anyList()))
                    .thenReturn(evidence(false));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should skip unrelated env vars")
        void shouldSkipUnrelatedEnvVars() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"OTHER_VAR=value", "PATH=/bin"});
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.listTags(ref("nginx:latest"))).thenReturn(catalogue("1.26.0"));
            when(registryService.readManifests(eq(ref("nginx:latest")), anyList()))
                    .thenReturn(evidence(false));

            VersionInfo result = versionService
                    .resolve(new com.roomelephant.porthole.core.model.ContainerKey("local", "container1"), null)
                    .toVersionInfo();

            assertEquals("latest", result.currentVersion());
        }
    }

    private void setupContainerWithImage(String image) {
        when(dockerClient.inspectContainerCmd("container1")).thenReturn(inspectContainerCmd);
        when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
        when(inspectContainerResponse.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getImage()).thenReturn(image);
    }

    private void setupRemoteImage() {
        when(inspectContainerResponse.getImageId()).thenReturn("sha256:abc123");
        when(dockerClient.inspectImageCmd("sha256:abc123")).thenReturn(inspectImageCmd);
        when(inspectImageCmd.exec()).thenReturn(inspectImageResponse);
        String repository = com.roomelephant.porthole.core.model.ImageReference.tryParse(containerConfig.getImage())
                .orElseThrow()
                .canonicalRepository();
        when(inspectImageResponse.getRepoDigests()).thenReturn(List.of(repository + "@" + LOCAL_DIGEST));
    }
}
