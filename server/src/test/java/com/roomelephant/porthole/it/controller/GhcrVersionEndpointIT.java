package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.domain.model.ImageReference;
import com.roomelephant.porthole.domain.model.VersionInfo;
import com.roomelephant.porthole.it.infra.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class GhcrVersionEndpointIT extends IntegrationTestBase {
    private static final String REPOSITORY = "graalvm/native-image-community";
    private static final String SOURCE = "ghcr.io/" + REPOSITORY + ":25";
    private static final String TAG = "0.0." + System.currentTimeMillis();
    private static final String IMAGE = "ghcr.io/" + REPOSITORY + ":" + TAG;
    private static final String INDEX = "application/vnd.oci.image.index.v1+json";
    private static final String DIFFERENT = "sha256:" + "b".repeat(64);
    private GenericContainer<?> fixture;
    private GenericContainer<?> pinned;
    private static String digest;

    @BeforeAll
    static void startGhcrFixture() throws InterruptedException {
        var docker = DockerClientFactory.instance().client();
        docker.pullImageCmd(SOURCE).start().awaitCompletion();
        var image = ImageReference.tryParse(SOURCE).orElseThrow();
        List<String> digests = docker.inspectImageCmd(SOURCE).exec().getRepoDigests();
        digest = image.matchingDigests(digests).getFirst();
        docker.tagImageCmd(SOURCE, "ghcr.io/" + REPOSITORY, TAG).exec();
    }

    @org.junit.jupiter.api.BeforeEach
    void prepareContainers() {
        fixture = sleeper(IMAGE);
        pinned = sleeper("ghcr.io/" + REPOSITORY + "@" + digest);
    }

    private static GenericContainer<?> sleeper(String image) {
        return new GenericContainer<>(image)
                .withImagePullPolicy(name -> false)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
                .withCommand("-c", "echo ready; sleep 3600")
                .withEnv("NATIVE_IMAGE_COMMUNITY_VERSION", "1.0")
                .waitingFor(Wait.forLogMessage(".*ready.*", 1));
    }

    @AfterAll
    static void stopGhcrFixture() {
        DockerClientFactory.instance().client().removeImageCmd(IMAGE).exec();
    }

    @Test
    void numericGhcrUpdateUsesEveryTagPageDespiteMissingManifest() {
        auth();
        wireMockClient()
                .stubFor(get(urlEqualTo("/ghcr/v2/" + REPOSITORY + "/tags/list?n=100"))
                        .withHeader("Authorization", equalTo("Bearer ghcr-token"))
                        .willReturn(okJson("{\"tags\":[\"1.0\"]}")
                                .withHeader(
                                        "Link",
                                        "</ghcr/v2/" + REPOSITORY + "/tags/list?n=100&last=1.0>; rel=\"next\"")));
        wireMockClient()
                .stubFor(get(urlEqualTo("/ghcr/v2/" + REPOSITORY + "/tags/list?n=100&last=1.0"))
                        .withHeader("Authorization", equalTo("Bearer ghcr-token"))
                        .willReturn(okJson("{\"tags\":[\"2.0\"]}")));
        wireMockClient()
                .stubFor(head(urlEqualTo("/ghcr/v2/" + REPOSITORY + "/manifests/" + TAG))
                        .willReturn(aResponse().withStatus(404)));
        var version = fetchVersion(fixture);
        assertThat(version.currentVersion()).isEqualTo("1.0");
        assertThat(version.latestVersion()).isEqualTo("2.0");
        assertThat(version.updateAvailable()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"same,true,false", "different,true,true", "different,false,false"})
    void ghcrWarningsRequireComparableManifestEvidence(String remote, boolean compatible, boolean expected) {
        auth();
        wireMockClient()
                .stubFor(get(urlEqualTo("/ghcr/v2/" + REPOSITORY + "/tags/list?n=100"))
                        .willReturn(okJson("{\"tags\":[\"1.0\"]}")));
        manifest(TAG, remote.equals("same") ? digest : DIFFERENT, INDEX);
        if (!remote.equals("same")) {
            manifest(digest, digest, compatible ? INDEX : "application/vnd.oci.image.manifest.v1+json");
        }
        assertThat(fetchVersion(fixture).updateAvailable()).isEqualTo(expected);
    }

    @Test
    void pinnedGhcrImageNeverContactsAnyRegistry() {
        assertThat(fetchVersion(pinned).updateAvailable()).isFalse();
        assertThat(wireMockClient().getAllServeEvents().stream()
                        .filter(event -> event.getRequest().getUrl().contains(REPOSITORY)))
                .isEmpty();
    }

    private void auth() {
        wireMockClient()
                .stubFor(get(urlEqualTo("/ghcr/token?service=ghcr.io&scope=repository:" + REPOSITORY + ":pull"))
                        .willReturn(okJson("{\"token\":\"ghcr-token\"}")));
    }

    private void manifest(String reference, String value, String type) {
        wireMockClient()
                .stubFor(head(urlEqualTo("/ghcr/v2/" + REPOSITORY + "/manifests/" + reference))
                        .withHeader("Authorization", equalTo("Bearer ghcr-token"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", type)
                                .withHeader("Docker-Content-Digest", value)));
    }

    @org.junit.jupiter.api.AfterEach
    void stopFixtures() {
        stopPorthole();
        pinned.stop();
        fixture.stop();
    }

    private VersionInfo fetchVersion(GenericContainer<?> container) {
        if (!container.isRunning()) container.start();
        return fetchCachedVersion(container.getContainerId());
    }
}
