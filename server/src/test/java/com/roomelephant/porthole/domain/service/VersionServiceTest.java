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
import com.roomelephant.porthole.domain.model.VersionDTO;
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
        versionService = new VersionService(dockerClient, registryService);
    }

    @Nested
    @DisplayName("getVersionInfo")
    class GetVersionInfo {

        @Test
        @DisplayName("should throw NotFoundException when container not found")
        void shouldThrowNotFoundExceptionWhenContainerNotFound() {
            when(dockerClient.inspectContainerCmd("unknown")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec())
                    .thenThrow(new com.github.dockerjava.api.exception.NotFoundException("Not found"));

            NotFoundException exception =
                    assertThrows(NotFoundException.class, () -> versionService.getVersionInfo("unknown"));

            assertEquals("unknown", exception.getContainerId());
        }

        @Test
        @DisplayName("should throw UnexpectedException when docker fails")
        void shouldThrowUnexpectedExceptionWhenDockerFails() {
            when(dockerClient.inspectContainerCmd("container1")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec()).thenThrow(new RuntimeException("Docker error"));

            assertThrows(UnexpectedException.class, () -> versionService.getVersionInfo("container1"));
        }

        @Test
        @DisplayName("should return empty VersionDTO when container config is null")
        void shouldReturnEmptyVersionDTOWhenContainerConfigIsNull() {
            when(dockerClient.inspectContainerCmd("container1")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
            when(inspectContainerResponse.getConfig()).thenReturn(null);

            VersionDTO result = versionService.getVersionInfo("container1");

            assertNull(result.currentVersion());
            assertNull(result.latestVersion());
            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should return empty VersionDTO when image is null")
        void shouldReturnEmptyVersionDTOWhenImageIsNull() {
            when(dockerClient.inspectContainerCmd("container1")).thenReturn(inspectContainerCmd);
            when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
            when(inspectContainerResponse.getConfig()).thenReturn(containerConfig);
            when(containerConfig.getImage()).thenReturn(null);

            VersionDTO result = versionService.getVersionInfo("container1");

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

            VersionDTO result = versionService.getVersionInfo("container1");

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
            when(registryService.getDigest("nginx:latest", "latest")).thenReturn("sha256:remote");

            VersionDTO result = versionService.getVersionInfo("container1");

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
            when(registryService.getDigest("myapp:latest", "latest")).thenReturn("sha256:remote");

            VersionDTO result = versionService.getVersionInfo("container1");

            assertEquals("2.0.0", result.currentVersion());
        }

        @Test
        @DisplayName("should return version from environment variable")
        void shouldReturnVersionFromEnvironmentVariable() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"NGINX_VERSION=1.25.0"});
            when(registryService.getLatestVersion("nginx:latest")).thenReturn("1.26.0");
            when(registryService.getDigest("nginx:latest", "latest")).thenReturn("sha256:remote");

            VersionDTO result = versionService.getVersionInfo("container1");

            assertEquals("1.25.0", result.currentVersion());
        }

        @Test
        @DisplayName("should return version from generic VERSION env var")
        void shouldReturnVersionFromGenericVersionEnvVar() {
            setupContainerWithImage("myapp:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"VERSION=3.0.0"});
            when(registryService.getLatestVersion("myapp:latest")).thenReturn("3.1.0");
            when(registryService.getDigest("myapp:latest", "latest")).thenReturn("sha256:remote");

            VersionDTO result = versionService.getVersionInfo("container1");

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
            when(registryService.getDigest("nginx:1.25", "1.25")).thenReturn("sha256:different");

            VersionDTO result = versionService.getVersionInfo("container1");

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
            when(registryService.getDigest("nginx:1.25", "1.25")).thenReturn("sha256:local");

            VersionDTO result = versionService.getVersionInfo("container1");

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
            when(registryService.getDigest("nginx:1.25", "1.25")).thenReturn("sha256:local");

            VersionDTO result = versionService.getVersionInfo("container1");

            assertTrue(result.updateAvailable());
        }

        @Test
        @DisplayName("should handle registry service errors gracefully")
        void shouldHandleRegistryServiceErrorsGracefully() {
            setupContainerWithImage("nginx:1.25");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.getLatestVersion("nginx:1.25")).thenReturn(null);
            when(registryService.getDigest("nginx:1.25", "1.25")).thenThrow(new RuntimeException("Network error"));

            VersionDTO result = versionService.getVersionInfo("container1");

            assertNotNull(result);
            assertFalse(result.updateAvailable());
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

            VersionDTO result = versionService.getVersionInfo("container1");

            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should handle inspectImage exception")
        void shouldHandleInspectImageException() {
            setupContainerWithImage("nginx:1.25");
            when(inspectContainerResponse.getImageId()).thenReturn("sha256:abc123");
            when(dockerClient.inspectImageCmd("sha256:abc123")).thenReturn(inspectImageCmd);
            when(inspectImageCmd.exec()).thenThrow(new RuntimeException("Image not found"));
            when(containerConfig.getEnv()).thenReturn(null);
            when(containerConfig.getLabels()).thenReturn(null);

            VersionDTO result = versionService.getVersionInfo("container1");

            assertEquals("1.25", result.currentVersion());
            assertNull(result.latestVersion());
            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("should skip empty env var values")
        void shouldSkipEmptyEnvVarValues() {
            setupContainerWithImage("nginx:latest");
            setupRemoteImage();
            when(containerConfig.getEnv()).thenReturn(new String[] {"NGINX_VERSION=", "VERSION="});
            when(containerConfig.getLabels()).thenReturn(null);
            when(registryService.getLatestVersion("nginx:latest")).thenReturn("1.26.0");
            when(registryService.getDigest("nginx:latest", "latest")).thenReturn("sha256:remote");

            VersionDTO result = versionService.getVersionInfo("container1");

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
            when(registryService.getDigest("nginx:1.25", "1.25")).thenReturn(null);

            VersionDTO result = versionService.getVersionInfo("container1");

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
            when(registryService.getDigest("nginx:latest", "latest")).thenReturn("sha256:local");

            VersionDTO result = versionService.getVersionInfo("container1");

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
            when(registryService.getDigest("nginx:1.25", "1.25")).thenReturn("sha256:local");

            VersionDTO result = versionService.getVersionInfo("container1");

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
            when(registryService.getDigest("nginx:latest", "latest")).thenReturn("sha256:local");

            VersionDTO result = versionService.getVersionInfo("container1");

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
        when(inspectImageResponse.getRepoDigests()).thenReturn(List.of("nginx@sha256:local"));
    }
}
