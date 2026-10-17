package com.roomelephant.porthole.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.ContainerConfig;
import com.roomelephant.porthole.domain.component.RegistryService;
import com.roomelephant.porthole.domain.model.VersionInfo;
import com.roomelephant.porthole.domain.model.exception.NotFoundException;
import com.roomelephant.porthole.domain.model.exception.UnexpectedException;
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
    private RegistryService registryService;

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

    @BeforeEach
    void setUp() {
        versionService =
                new VersionService(com.roomelephant.porthole.DockerTestSources.of(dockerClient), registryService);
    }

    @Nested
    @DisplayName("getVersionInfo")
    class GetVersionInfo {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(
                strings = {
                    "quay.io/acme/app:1.0",
                    "ghcr.io/acme/app@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "ghcr.io/app",
                    "localhost:5000/app",
                    "nginx@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "nginx:1.25@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                })
        void unsupportedOrPinnedImagesDoNotQueryRegistry(String image) {
            setupContainerWithImage(image);
            assertFalse(versionService
                    .getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"))
                    .updateAvailable());
            verifyNoInteractions(registryService);
            verify(dockerClient, never()).inspectImageCmd(anyString());
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
            when(registryService.getLatestVersion("nginx:1.0")).thenReturn(latest);
            when(registryService.isTagChanged(eq("nginx:1.0"), anyList())).thenReturn(false);
            assertEquals(
                    expected,
                    versionService
                            .getVersionInfo(
                                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"))
                            .updateAvailable());
        }

        @Test
        void specificVersionWinsEvenWhenGenericAppearsFirst() {
            setupContainerWithImage("nginx:1.0");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"VERSION=9.9", "NGINX_VERSION=1.0"});
            when(registryService.getLatestVersion("nginx:1.0")).thenReturn("1.0");
            when(registryService.isTagChanged(eq("nginx:1.0"), anyList())).thenReturn(false);
            assertEquals(
                    "1.0",
                    versionService
                            .getVersionInfo(
                                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"))
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
                when(registryService.getLatestVersion(image)).thenReturn("2.0");
                when(registryService.isTagChanged(image, List.of(LOCAL_DIGEST))).thenReturn(false);
            }
            assertEquals(
                    eligible,
                    versionService
                            .getVersionInfo(
                                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"))
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
                            .getVersionInfo(
                                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"))
                            .currentVersion());
        }

        @Test
        void pinnedReferenceRetainsExplicitTagForDisplay() {
            setupContainerWithImage("nginx:1.25@" + LOCAL_DIGEST);
            assertEquals(
                    "1.25",
                    versionService
                            .getVersionInfo(
                                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"))
                            .currentVersion());
            verifyNoInteractions(registryService);
        }

        @Test
        void unrelatedImageProvenanceDoesNotQueryRegistry() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(inspectImageResponse.getRepoDigests()).thenReturn(List.of("ghcr.io/acme/nginx@" + LOCAL_DIGEST));
            assertFalse(versionService
                    .getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"))
                    .updateAvailable());
            verifyNoInteractions(registryService);
        }

        @Test
        @DisplayName("should throw NotFoundException when container not found")
        void shouldThrowNotFoundExceptionWhenContainerNotFound() {
            when(dockerClient.inspectContainerCmd("unknown")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec())
                    .thenThrow(new com.github.dockerjava.api.exception.NotFoundException("Not found"));

            NotFoundException exception = assertThrows(
                    NotFoundException.class,
                    () -> versionService.getVersionInfo(
                            new com.roomelephant.porthole.domain.model.ContainerKey("local", "unknown")));

            assertEquals("unknown", exception.getContainerId());
        }

        @Test
        @DisplayName("should throw UnexpectedException when docker fails")
        void shouldThrowUnexpectedExceptionWhenDockerFails() {
            when(dockerClient.inspectContainerCmd("container1")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec()).thenThrow(new RuntimeException("Docker error"));

            assertThrows(
                    UnexpectedException.class,
                    () -> versionService.getVersionInfo(
                            new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1")));
        }

        @Test
        @DisplayName("should return empty VersionInfo when container config is null")
        void shouldReturnEmptyVersionInfoWhenContainerConfigIsNull() {
            when(dockerClient.inspectContainerCmd("container1")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
            when(inspectContainerResponse.getConfig()).thenReturn(null);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

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

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

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

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

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
            when(registryService.getLatestVersion("nginx:latest")).thenReturn("1.26.0");
            when(registryService.isTagChanged(eq("nginx:latest"), anyList())).thenReturn(true);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

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
            when(registryService.getLatestVersion("myapp:latest")).thenReturn("2.1.0");
            when(registryService.isTagChanged(eq("myapp:latest"), anyList())).thenReturn(true);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

            assertEquals("2.0.0", result.currentVersion());
        }

        @Test
        @DisplayName("should return version from environment variable")
        void shouldReturnVersionFromEnvironmentVariable() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"NGINX_VERSION=1.25.0"});
            when(registryService.getLatestVersion("nginx:latest")).thenReturn("1.26.0");
            when(registryService.isTagChanged(eq("nginx:latest"), anyList())).thenReturn(true);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

            assertEquals("1.25.0", result.currentVersion());
        }

        @Test
        @DisplayName("should return version from generic VERSION env var")
        void shouldReturnVersionFromGenericVersionEnvVar() {
            setupContainerWithImage("myapp:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"VERSION=3.0.0"});
            when(registryService.getLatestVersion("myapp:latest")).thenReturn("3.1.0");
            when(registryService.isTagChanged(eq("myapp:latest"), anyList())).thenReturn(true);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

            assertEquals("3.0.0", result.currentVersion());
        }

        @Test
        @DisplayName("should detect update available when digest differs")
        void shouldDetectUpdateAvailableWhenDigestDiffers() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.getLatestVersion("nginx:1.25")).thenReturn("1.26");
            when(registryService.isTagChanged(eq("nginx:1.25"), anyList())).thenReturn(true);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

            assertTrue(result.updateAvailable());
        }

        @Test
        @DisplayName("should not detect update when digest matches")
        void shouldNotDetectUpdateWhenDigestMatches() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.getLatestVersion("nginx:1.25")).thenReturn("1.25");
            when(registryService.isTagChanged(eq("nginx:1.25"), anyList())).thenReturn(false);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should detect update when semver versions differ")
        void shouldDetectUpdateWhenSemverVersionsDiffer() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(Map.of("org.opencontainers.image.version", "1.25.0"));
            when(registryService.getLatestVersion("nginx:1.25")).thenReturn("1.26.0");
            when(registryService.isTagChanged(eq("nginx:1.25"), anyList())).thenReturn(false);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

            assertTrue(result.updateAvailable());
        }

        @Test
        @DisplayName("should propagate registry service errors")
        void shouldPropagateRegistryServiceErrors() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.getLatestVersion("nginx:1.25")).thenReturn(null);
            when(registryService.isTagChanged(eq("nginx:1.25"), anyList()))
                    .thenThrow(new RuntimeException("Network error"));

            assertThrows(
                    RuntimeException.class,
                    () -> versionService.getVersionInfo(
                            new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1")));
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

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

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

            assertThrows(
                    RuntimeException.class,
                    () -> versionService.getVersionInfo(
                            new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1")));
        }

        @Test
        @DisplayName("should skip empty env var values")
        void shouldSkipEmptyEnvVarValues() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"NGINX_VERSION=", "VERSION="});
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.getLatestVersion("nginx:latest")).thenReturn("1.26.0");
            when(registryService.isTagChanged(eq("nginx:latest"), anyList())).thenReturn(true);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

            assertEquals("latest", result.currentVersion());
        }

        @Test
        @DisplayName("should not detect update when remote digest is null")
        void shouldNotDetectUpdateWhenRemoteDigestIsNull() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.getLatestVersion("nginx:1.25")).thenReturn("1.25");
            when(registryService.isTagChanged(eq("nginx:1.25"), anyList())).thenReturn(false);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should not detect update for non-semver tag with null currentVersion")
        void shouldNotDetectUpdateForNonSemverTagWithNullCurrentVersion() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.getLatestVersion("nginx:latest")).thenReturn("1.26.0");
            when(registryService.isTagChanged(eq("nginx:latest"), anyList())).thenReturn(false);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should not detect update when current and latest versions are equal")
        void shouldNotDetectUpdateWhenCurrentAndLatestVersionsAreEqual() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(Map.of("org.opencontainers.image.version", "1.26.0"));
            when(registryService.getLatestVersion("nginx:1.25")).thenReturn("1.26.0");
            when(registryService.isTagChanged(eq("nginx:1.25"), anyList())).thenReturn(false);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should skip unrelated env vars")
        void shouldSkipUnrelatedEnvVars() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"OTHER_VAR=value", "PATH=/bin"});
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.getLatestVersion("nginx:latest")).thenReturn("1.26.0");
            when(registryService.isTagChanged(eq("nginx:latest"), anyList())).thenReturn(false);

            VersionInfo result = versionService.getVersionInfo(
                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "container1"));

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
        String repository = com.roomelephant.porthole.domain.model.ImageReference.tryParse(containerConfig.getImage())
                .orElseThrow()
                .canonicalRepository();
        when(inspectImageResponse.getRepoDigests()).thenReturn(List.of(repository + "@" + LOCAL_DIGEST));
    }
}
