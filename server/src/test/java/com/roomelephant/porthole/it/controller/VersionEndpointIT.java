package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

import com.github.dockerjava.api.command.InspectImageResponse;
import com.roomelephant.porthole.domain.model.VersionDTO;
import com.roomelephant.porthole.it.infra.ContainerAwareIntegrationTestBase;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class VersionEndpointIT extends ContainerAwareIntegrationTestBase {
    private static final String HUB_IMAGE = "docker.io/library/busybox:1.36.1";
    private static final String MUTABLE_TAG = "porthole-it-" + java.util.UUID.randomUUID();
    private static final String HUB_LATEST = "docker.io/library/busybox:" + MUTABLE_TAG;
    private static final org.testcontainers.containers.GenericContainer<?> hubVersion;
    private static final org.testcontainers.containers.GenericContainer<?> hubLatest;

    static {
        hubVersion = new org.testcontainers.containers.GenericContainer<>(HUB_IMAGE)
                .withCommand("sh", "-c", "echo ready; sleep 3600")
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*ready.*", 1));
        hubVersion.start();
        hubVersion
                .getDockerClient()
                .tagImageCmd(HUB_IMAGE, "docker.io/library/busybox", MUTABLE_TAG)
                .exec();
        hubLatest = new org.testcontainers.containers.GenericContainer<>(HUB_LATEST)
                .withImagePullPolicy(image -> false)
                .withCommand("sh", "-c", "echo ready; sleep 3600")
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*ready.*", 1));
        hubLatest.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            hubLatest.stop();
            hubVersion.stop();
            hubVersion.getDockerClient().removeImageCmd(HUB_LATEST).exec();
        }));
    }

    @Test
    void foreignRegistryNeverContactsDockerHub() {
        assertThat(fetchVersion(testAppContainer.getContainerId()).updateAvailable())
                .isFalse();
        assertThat(wireMockClient().getAllServeEvents()).isEmpty();
    }

    @Test
    void shouldReturnUpdateAvailableWhenNewerVersionExists() {
        stubAuth();
        stubRegistryTags("busybox", "1.36.1", "1.37.0");
        stubManifestDigest("busybox", "1.36.1", getLocalDigest(HUB_IMAGE));

        VersionDTO response = fetchVersion(hubVersion.getContainerId());

        assertThat(response.currentVersion()).isEqualTo("1.36.1");
        assertThat(response.latestVersion()).isEqualTo("1.37.0");
        assertThat(response.updateAvailable()).isTrue();
    }

    @Test
    void retaggedImageWithMissingRemoteRepositoryHasNoUpdate() {
        var localReference = com.roomelephant.porthole.domain.model.ImageReference.tryParse(TEST_LOCAL_IMAGE_TAG)
                .orElseThrow();
        var digests = localContainer
                .getDockerClient()
                .inspectImageCmd(TEST_LOCAL_IMAGE_TAG)
                .exec()
                .getRepoDigests();
        boolean comparable =
                digests != null && !localReference.matchingDigests(digests).isEmpty();
        if (comparable) {
            stubAuth();
            stubRegistryTags404("my-local-image");
            stubManifest404("my-local-image", "1.0");
        }

        VersionDTO response = fetchVersion(localContainer.getContainerId());

        if (!comparable) assertThat(wireMockClient().getAllServeEvents()).isEmpty();
        assertThat(response.currentVersion()).isEqualTo("1.0");
        assertThat(response.latestVersion()).isNull();
        assertThat(response.updateAvailable()).isFalse();
    }

    @Test
    void shouldReturn404WhenContainerNotFound() {
        String nonExistentId = "non-existent-id";

        ResponseEntity<String> response = fetch("/api/containers/" + nonExistentId + "/version");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturnNoUpdateWhenLatestTagMatchesDigest() {
        stubAuth();
        String containerId = hubLatest.getContainerId();
        String currentDigest = getLocalDigest(HUB_LATEST);

        stubRegistryTags("busybox", MUTABLE_TAG);
        stubManifestDigest("busybox", MUTABLE_TAG, currentDigest);

        VersionDTO response = fetchVersion(containerId);

        assertThat(response.currentVersion()).isEqualTo(MUTABLE_TAG);
        assertThat(response.latestVersion()).isNull();
        assertThat(response.updateAvailable()).isFalse();
    }

    @Test
    void shouldReturnUpdateWhenLatestTagAndDigestIsOlder() {
        stubAuth();
        String containerId = hubLatest.getContainerId();

        stubRegistryTags("busybox", MUTABLE_TAG);
        stubManifestDigest("busybox", MUTABLE_TAG, "sha256:" + "b".repeat(64));
        stubManifestDigest("busybox", getLocalDigest(HUB_LATEST), getLocalDigest(HUB_LATEST));

        VersionDTO response = fetchVersion(containerId);

        assertThat(response.currentVersion()).isEqualTo(MUTABLE_TAG);
        assertThat(response.latestVersion()).isNull();
        assertThat(response.updateAvailable()).isTrue();
    }

    @Test
    void shouldReturnUpdateWhenLatestTagAndCurrentVersionIsOlder() {
        stubAuth();
        String containerId = hubLatest.getContainerId();

        stubRegistryTags("busybox", MUTABLE_TAG, "3.9");
        stubManifestDigest("busybox", MUTABLE_TAG, "sha256:" + "c".repeat(64));
        stubManifestDigest("busybox", getLocalDigest(HUB_LATEST), getLocalDigest(HUB_LATEST));

        VersionDTO response = fetchVersion(containerId);

        assertThat(response.updateAvailable()).isTrue();
    }

    private String getLocalDigest(String imageName) {
        InspectImageResponse inspect =
                hubLatest.getDockerClient().inspectImageCmd(imageName).exec();
        return inspect.getRepoDigests().stream()
                .findFirst()
                .map(d -> d.contains("@") ? d.substring(d.indexOf("@") + 1) : d)
                .orElseGet(inspect::getId);
    }

    void stubAuth() {
        wireMockClient()
                .stubFor(get(urlMatching("/auth.*"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"token\":\"mock-token\"}")));
    }

    void stubRegistryTags404(String image) {
        wireMockClient()
                .stubFor(get(urlEqualTo("/v2/repositories/library/" + image + "/tags?page_size=100"))
                        .willReturn(aResponse().withStatus(404)));
    }

    void stubManifest404(String image, String version) {
        wireMockClient()
                .stubFor(head(urlMatching("/v2/library/" + image + "/manifests/" + version))
                        .willReturn(aResponse().withStatus(404)));
    }

    protected @NotNull VersionDTO fetchVersion(String containerId) {
        ResponseEntity<VersionDTO> response = fetch("/api/containers/" + containerId + "/version", VersionDTO.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);

        assertThat(response.getBody()).isNotNull();

        return response.getBody();
    }

    /**
     * Stub Docker registry tags endpoint with given versions
     *
     *
     * @param image    Image name (e.g., "busybox")
     * @param versions Array of version strings (e.g., "1.35.0", "1.36.1")
     */
    void stubRegistryTags(String image, String... versions) {
        StringBuilder tagsJson = new StringBuilder("{\"results\": [");
        for (int i = 0; i < versions.length; i++) {
            if (i > 0) {
                tagsJson.append(",");
            }
            tagsJson.append("{\"name\": \"").append(versions[i]).append("\"}");
        }
        tagsJson.append("]}");

        wireMockClient()
                .stubFor(get(urlEqualTo("/v2/repositories/library/" + image + "/tags?page_size=100"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(tagsJson.toString())));
    }

    /**
     * Stub Docker registry manifest HEAD request with digest
     *
     * @param image   Image name (e.g., "busybox")
     * @param version Version tag (e.g., "1.36.1")
     * @param digest  Digest value (e.g., "sha256:newdigest")
     */
    void stubManifestDigest(String image, String version, String digest) {
        wireMockClient()
                .stubFor(head(urlEqualTo("/v2/library/" + image + "/manifests/" + version))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/vnd.docker.distribution.manifest.v2+json")
                                .withHeader("Docker-Content-Digest", digest)
                                .withBody("{}")));
    }
}
