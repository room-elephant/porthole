package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roomelephant.porthole.it.infra.DashboardView;
import com.roomelephant.porthole.it.infra.DashboardView.EndpointView;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

class ContainerEntriesIT {
    private static final String CONTAINER_LIST = "/(?:v[0-9.]+/)?containers/json";
    private WireMockServer docker;
    private GenericContainer<?> app;
    private String volume;
    private final RestTemplate http = new RestTemplate(new JdkClientHttpRequestFactory());

    @BeforeEach
    void start() {
        docker = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        docker.start();
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?_ping")).willReturn(ok("OK")));
        inventoryFixture("original-id");
        volume = "porthole-entries-it-" + UUID.randomUUID();
        DockerClientFactory.instance()
                .client()
                .createVolumeCmd()
                .withName(volume)
                .exec();
        startReplacement();
    }

    private void inventoryFixture(String id) {
        inventoryFixture(id, "{}", "[]");
    }

    private void inventoryFixture(String id, String labels, String ports) {
        docker.stubFor(get(urlPathMatching(CONTAINER_LIST)).willReturn(okJson("""
                        [{"Id":"%s","Names":["/shared-app"],"Image":"local-app:latest",
                        "ImageID":"local-image","State":"running","Status":"Up","Labels":%s,"Ports":%s}]
                        """.formatted(id, labels, ports))));
        if (app != null && app.isRunning()) awaitPollsAfterFixtureChange();
    }

    // Inventory is served from background snapshots; a second list call per source after the stub change means the
    // poll that read the new fixture has already been published.
    private void awaitPollsAfterFixtureChange() {
        int before = containerListCalls();
        await().atMost(Duration.ofSeconds(5)).until(() -> containerListCalls() >= before + 4);
    }

    private int containerListCalls() {
        return docker.countRequestsMatching(
                        getRequestedFor(urlPathMatching(CONTAINER_LIST)).build())
                .getCount();
    }

    private void startReplacement() {
        app = PortholeContainer.withCustomSocket("/tmp/unused.sock", docker.port());
        for (int index = 0; index < 2; index++) {
            String prefix = "PORTHOLE_DOCKER_CONNECTIONS_" + index + "_";
            app.withEnv(prefix + "ID", index == 0 ? "walker" : "mike")
                    .withEnv(prefix + "NAME", index == 0 ? "Walker" : "Mike")
                    .withEnv(prefix + "HOST", "tcp://host.docker.internal:" + docker.port());
        }
        app.withCreateContainerCmdModifier(cmd -> {
            var binds = new ArrayList<Bind>();
            if (cmd.getHostConfig().getBinds() != null)
                binds.addAll(Arrays.asList(cmd.getHostConfig().getBinds()));
            binds.add(new Bind(volume, new Volume("/app/data")));
            cmd.getHostConfig().withBinds(binds);
        });
        app.start();
    }

    @AfterEach
    void stop(TestInfo info) throws Exception {
        try {
            if (app != null && app.isRunning())
                Files.writeString(
                        Path.of("target", info.getTestMethod().orElseThrow().getName() + "-entries.log"),
                        app.getLogs());
        } finally {
            if (app != null) app.stop();
            if (volume != null)
                DockerClientFactory.instance().client().removeVolumeCmd(volume).exec();
            if (docker != null) docker.stop();
        }
    }

    private void select(String source, String endpoint) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/merge-patch+json"));
        var response = http.exchange(
                appUrl() + "/api/entries/container:" + source + ":shared-app",
                HttpMethod.PATCH,
                new HttpEntity<>("{\"endpoint\":" + endpoint + "}", headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    private String appUrl() {
        return "http://localhost:" + app.getMappedPort(PortholeContainer.PORTHOLE_PORT);
    }

    private DashboardView.Filtered inventory(boolean includeWithoutEndpoints) {
        String response = http.getForObject(appUrl() + DashboardView.PATH, String.class);
        return new DashboardView.Filtered(DashboardView.parse(response).containers(includeWithoutEndpoints, false));
    }

    private static final String CUSTOM = "{\"url\":\"https://shared.example.com/app\"}";

    @Test
    void emptyNamedVolumeSupportsNonrootSaveAndCustomOnlyInventoryWithoutMixingSources() throws Exception {
        assertThat(app.execInContainer("cat", "/proc/1/status").getStdout())
                .containsPattern("Uid:\\s+65532\\s+65532\\s+65532\\s+65532");
        assertThat(inventory(false).containers()).isEmpty();
        assertThat(inventory(true).containers())
                .allSatisfy(container -> assertThat(container.hasWebUi()).isFalse());
        select("mike", "\"none\"");
        select("walker", CUSTOM);
        assertThat(inventory(false).containers()).singleElement().satisfies(container -> {
            assertThat(container.sourceId()).isEqualTo("walker");
            assertThat(container.endpoints())
                    .containsExactly(new EndpointView("custom", "custom", "https://shared.example.com/app", true));
            assertThat(container.hasEndpoints()).isTrue();
            assertThat(container.hasWebUi()).isTrue();
        });
        assertThat(inventory(true).containers())
                .filteredOn(c -> c.sourceId().equals("mike"))
                .singleElement()
                .satisfies(c -> assertThat(c.hasWebUi()).isFalse());
        select("walker", CUSTOM);
        assertThat(inventory(false).containers())
                .singleElement()
                .satisfies(c -> assertThat(c.endpoints())
                        .containsExactly(new EndpointView("custom", "custom", "https://shared.example.com/app", true)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"none\"", "{\"id\":\"published-port:8080\"}"})
    void replacingCustomDiscardsItsUrlAndRestoresNormalFiltering(String replacement) throws Exception {
        select("walker", CUSTOM);
        select("walker", replacement);
        assertThat(inventory(false).containers()).isEmpty();
        assertThat(inventory(true).containers())
                .filteredOn(c -> c.sourceId().equals("walker"))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.hasWebUi()).isFalse();
                    assertThat(c.endpoints()).isEmpty();
                    assertThat(c.hasEndpoints()).isFalse();
                });
        var file = app.execInContainer("cat", "/app/data/state.json");
        assertThat(file.getExitCode()).isZero();
        assertThat(file.getStdout()).doesNotContain("https://shared.example.com/app");
    }

    @Test
    void replacingApplicationAndRecreatingContainerRetainsEntriesInReusedVolume() {
        select("walker", CUSTOM);
        String previousContainer = app.getContainerId();
        app.stop();
        inventoryFixture("recreated-id");
        startReplacement();
        assertThat(app.getContainerId()).isNotEqualTo(previousContainer);
        assertThat(inventory(false).containers()).singleElement().satisfies(container -> {
            assertThat(container.endpoints())
                    .containsExactly(new EndpointView("custom", "custom", "https://shared.example.com/app", true));
            assertThat(container.hasEndpoints()).isTrue();
            assertThat(container.sourceId()).isEqualTo("walker");
            assertThat(container.hasWebUi()).isTrue();
        });
    }

    private static final String PUBLISHED_PORT = "[{\"PrivatePort\":8080,\"PublicPort\":8080,\"Type\":\"tcp\"}]";

    @Test
    void resolvedCandidatesRetainDiscoveredChoicesAcrossCustomNoneAndAutomaticSelection() {
        inventoryFixture("original-id", "{}", PUBLISHED_PORT);
        assertThat(inventory(false).containers()).hasSize(2).allSatisfy(container -> {
            assertThat(container.endpoints())
                    .containsExactly(
                            new EndpointView("published-port:8080", "published-port", "http://127.0.0.1:8080/", true));
            assertThat(container.hasWebUi()).isTrue();
        });
        select("walker", CUSTOM);
        assertThat(inventory(false).containers())
                .filteredOn(c -> c.sourceId().equals("walker"))
                .singleElement()
                .satisfies(container -> assertThat(container.endpoints())
                        .containsExactly(
                                new EndpointView(
                                        "published-port:8080", "published-port", "http://127.0.0.1:8080/", false),
                                new EndpointView("custom", "custom", "https://shared.example.com/app", true)));
        select("walker", "\"none\"");
        assertThat(inventory(false).containers())
                .filteredOn(c -> c.sourceId().equals("walker"))
                .singleElement()
                .satisfies(container -> {
                    assertThat(container.endpoints())
                            .containsExactly(new EndpointView(
                                    "published-port:8080", "published-port", "http://127.0.0.1:8080/", false));
                    assertThat(container.hasEndpoints()).isTrue();
                    assertThat(container.hasWebUi()).isFalse();
                });
        select("walker", "null");
        assertThat(inventory(false).containers()).hasSize(2).allSatisfy(container -> {
            assertThat(container.endpoints())
                    .containsExactly(
                            new EndpointView("published-port:8080", "published-port", "http://127.0.0.1:8080/", true));
            assertThat(container.hasWebUi()).isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"published", "roots", "paths"})
    void unavailableSavedChoiceFallsBackWithoutRewritingEntries(String fallback) throws Exception {
        inventoryFixture("original-id", "{}", PUBLISHED_PORT);
        select("walker", "{\"id\":\"published-port:8080\"}");
        assertThat(inventory(false).containers())
                .filteredOn(c -> c.sourceId().equals("walker"))
                .singleElement()
                .satisfies(container -> assertThat(container.endpoints())
                        .containsExactly(new EndpointView(
                                "published-port:8080", "published-port", "http://127.0.0.1:8080/", true)));
        String before = stateFile();
        if (fallback.equals("published")) {
            inventoryFixture("original-id", "{}", PUBLISHED_PORT.replace("8080", "9090"));
        } else {
            String first = fallback.equals("roots") ? "" : " && PathPrefix(`/one`)";
            String second = fallback.equals("roots") ? "" : " && PathPrefix(`/two`)";
            String labels = """
                    {"traefik.http.routers.one.rule":"Host(`one.example.com`)%s",
                     "traefik.http.routers.two.rule":"Host(`two.example.com`)%s"}
                    """.formatted(first, second);
            inventoryFixture("original-id", labels, "[]");
        }
        assertThat(inventory(false).containers())
                .filteredOn(c -> c.sourceId().equals("walker"))
                .singleElement()
                .satisfies(container -> {
                    assertThat(container.hasWebUi()).isTrue();
                    assertThat(container.endpoints())
                            .noneMatch(endpoint -> endpoint.id().equals("published-port:8080"));
                    if (fallback.equals("published")) {
                        assertThat(container.endpoints())
                                .containsExactly(new EndpointView(
                                        "published-port:9090", "published-port", "http://127.0.0.1:9090/", true));
                    } else {
                        assertThat(container.endpoints()).hasSize(2).allMatch(endpoint -> !endpoint.selected());
                    }
                });
        assertThat(stateFile()).isEqualTo(before);
        assertThat(before).doesNotContain("\"selected\"", "\"endpoints\"", "http://127.0.0.1");
    }

    private String stateFile() throws Exception {
        var file = app.execInContainer("cat", "/app/data/state.json");
        assertThat(file.getExitCode()).isZero();
        return file.getStdout();
    }
}
