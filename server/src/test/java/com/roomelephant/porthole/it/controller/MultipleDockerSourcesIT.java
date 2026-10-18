package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.roomelephant.porthole.controller.model.*;
import com.roomelephant.porthole.domain.model.SourceStatus;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestTemplate;

class MultipleDockerSourcesIT {
    private WireMockServer walker, mike, registry;
    private PortholeContainer app;
    private final RestTemplate http = new RestTemplate();
    private static final String A = "sha256:" + "a".repeat(64), B = "sha256:" + "b".repeat(64);
    private static final String LIST = "/(?:v[0-9.]+/)?containers/json";

    @BeforeEach
    void start() {
        walker = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        mike = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        registry = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        walker.start();
        mike.start();
        registry.start();
        docker(walker, "walker", "1.0", A);
        docker(mike, "mike", "2.0", B);
        registry.stubFor(get(urlPathEqualTo("/auth")).willReturn(okJson("{\"token\":\"test\"}")));
        registry.stubFor(get(urlPathEqualTo("/v2/repositories/library/nginx/tags"))
                .willReturn(okJson("{\"results\":[{\"name\":\"2.0\"}],\"next\":null}")));
        for (String ref : List.of("latest", A, B))
            registry.stubFor(head(urlEqualTo("/v2/library/nginx/manifests/" + ref))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/vnd.oci.image.manifest.v1+json")
                            .withHeader("Docker-Content-Digest", ref.equals(A) ? A : B)));
        app = new PortholeContainer(registry.port());
        connection(0, "walker", walker.port(), "192.168.50.6");
        connection(1, "mike", mike.port(), "192.168.50.5");
        app.withEnv("PORTHOLE_REGISTRY_CACHE_TTL", "1h");
        app.start();
    }

    private void connection(int index, String id, int port, String host) {
        String prefix = "PORTHOLE_DOCKER_CONNECTIONS_" + index + "_";
        app.withEnv(prefix + "ID", id)
                .withEnv(prefix + "NAME", id.equals("walker") ? "Walker" : "Mike")
                .withEnv(prefix + "HOST", "tcp://host.docker.internal:" + port)
                .withEnv(prefix + "PUBLISHEDHOST", host)
                .withEnv(prefix + "CONNECTIONTIMEOUT", "500ms")
                .withEnv(prefix + "RESPONSETIMEOUT", "1s");
    }

    private void docker(WireMockServer docker, String source, String version, String digest) {
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?_ping")).willReturn(ok("OK")));
        docker.stubFor(
                get(urlPathMatching(LIST))
                        .willReturn(
                                okJson(
                                        "[{\"Id\":\"same-id\",\"Names\":[\"/shared-app-1\"],\"Image\":\"nginx:latest\",\"ImageID\":\"image-"
                                                + source
                                                + "\",\"State\":\"running\",\"Status\":\"Up\",\"Labels\":{\"com.docker.compose.project\":\"shared\"},\"Ports\":[{\"PrivatePort\":80,\"PublicPort\":8080,\"Type\":\"tcp\"}]}]")));
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?containers/same-id/json"))
                .willReturn(okJson("{\"Id\":\"same-id\",\"Image\":\"image-" + source
                        + "\",\"Config\":{\"Image\":\"nginx:latest\",\"Env\":[\"NGINX_VERSION=" + version + "\"]}}")));
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?images/image-" + source + "/json"))
                .willReturn(okJson("{\"Id\":\"image-" + source + "\",\"RepoDigests\":[\"nginx@" + digest + "\"]}")));
    }

    private ContainerInventoryDTO inventory() {
        return http.getForObject(app.baseUrl() + "/api/containers", ContainerInventoryDTO.class);
    }

    @AfterEach
    void stop(org.junit.jupiter.api.TestInfo info) throws Exception {
        if (app != null && app.isRunning())
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(
                            "target",
                            info.getTestMethod()
                                            .map(java.lang.reflect.Method::getName)
                                            .orElse("multi-source") + "-container.log"),
                    app.getLogs());
        if (app != null) app.stop();
        if (walker != null) walker.stop();
        if (mike != null) mike.stop();
        if (registry != null) registry.stop();
    }

    @Test
    void oneRequestCombinesMatchingIdsAndKeepsInstalledVersionsSeparate() {
        var result = inventory();
        assertThat(result.sources()).extracting(SourceDTO::id).containsExactly("walker", "mike");
        assertThat(result.containers()).extracting(ContainerDTO::sourceId).containsExactly("walker", "mike");
        assertThat(result.containers()).extracting(ContainerDTO::id).containsExactly("same-id", "same-id");
        assertThat(result.containers().get(0).endpoints().getFirst().url()).isEqualTo("http://192.168.50.6:8080/");
        assertThat(result.containers().get(1).endpoints().getFirst().url()).isEqualTo("http://192.168.50.5:8080/");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var containers = inventory().containers();
            assertThat(containers).allSatisfy(c -> assertThat(c.version()).isNotNull());
            assertThat(containers.get(0).version().currentVersion()).isEqualTo("1.0");
            assertThat(containers.get(0).version().updateAvailable()).isTrue();
            assertThat(containers.get(1).version().currentVersion()).isEqualTo("2.0");
            assertThat(containers.get(1).version().updateAvailable()).isFalse();
        });
        walker.verify(getRequestedFor(urlPathMatching("/(?:v[0-9.]+/)?images/image-walker/json")));
        mike.verify(getRequestedFor(urlPathMatching("/(?:v[0-9.]+/)?images/image-mike/json")));
        registry.verify(1, getRequestedFor(urlPathEqualTo("/v2/repositories/library/nginx/tags")));
    }

    @Test
    void partialOutagePreservesHealthyResultsAndRecoveryRestoresSource() {
        var failure = mike.stubFor(get(urlPathMatching(LIST))
                .atPriority(1)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        var result = inventory();
        assertThat(result.containers()).extracting(ContainerDTO::sourceId).containsExactly("walker");
        assertThat(result.sources()).extracting(SourceDTO::status).containsExactly(SourceStatus.UP, SourceStatus.DOWN);
        mike.removeStub(failure);
        assertThat(inventory().containers()).extracting(ContainerDTO::sourceId).containsExactly("walker", "mike");
    }

    @Test
    void allUnavailableSourcesRemainDistinctFromConnectedEmptySources() {
        var empty = mike.stubFor(get(urlPathMatching(LIST)).atPriority(1).willReturn(okJson("[]")));
        var healthy = inventory();
        assertThat(healthy.sources().get(1).status()).isEqualTo(SourceStatus.UP);
        assertThat(healthy.containers()).extracting(ContainerDTO::sourceId).containsExactly("walker");
        mike.removeStub(empty);
        for (var docker : List.of(walker, mike))
            docker.stubFor(get(urlPathMatching(LIST))
                    .atPriority(1)
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        var down = inventory();
        assertThat(down.containers()).isEmpty();
        assertThat(down.sources()).extracting(SourceDTO::status).containsExactly(SourceStatus.DOWN, SourceStatus.DOWN);
    }
}
