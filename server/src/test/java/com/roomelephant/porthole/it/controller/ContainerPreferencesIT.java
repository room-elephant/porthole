package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roomelephant.porthole.inbound.web.dto.*;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

/** Exercises the shipped image, including its default data path and nonroot permissions. */
class ContainerPreferencesIT {
    private WireMockServer docker;
    private GenericContainer<?> app;
    private String volume;
    private final RestTemplate http = new RestTemplate();

    @BeforeEach
    void start() {
        docker = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        docker.start();
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?_ping")).willReturn(ok("OK")));
        inventoryFixture("original-id");
        volume = "porthole-preferences-it-" + UUID.randomUUID();
        DockerClientFactory.instance()
                .client()
                .createVolumeCmd()
                .withName(volume)
                .exec();
        startReplacement();
    }

    private void inventoryFixture(String id) {
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?containers/json")).willReturn(okJson("""
                        [{"Id":"%s","Names":["/shared-app"],"Image":"local-app:latest",
                        "ImageID":"local-image","State":"running","Status":"Up","Labels":{},"Ports":[]}]
                        """.formatted(id))));
    }

    private void startReplacement() {
        // No host socket: the image's entrypoint must drop to UID 65532.
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
                        Path.of("target", info.getTestMethod().orElseThrow().getName() + "-preferences.log"),
                        app.getLogs());
        } finally {
            if (app != null) app.stop();
            if (volume != null)
                DockerClientFactory.instance().client().removeVolumeCmd(volume).exec();
            if (docker != null) docker.stop();
        }
    }

    private SelectContainerEndpointResponse select(String source, String selection) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"sourceId":"%s","containerName":"shared-app","preferences":{"selectedEndpoint":%s}}
                """.formatted(source, selection);
        return http.exchange(
                        appUrl() + "/api/containers/selected-url",
                        HttpMethod.PUT,
                        new HttpEntity<>(body, headers),
                        SelectContainerEndpointResponse.class)
                .getBody();
    }

    private String appUrl() {
        return "http://localhost:" + app.getMappedPort(PortholeContainer.PORTHOLE_PORT);
    }

    private ContainerInventoryDTO inventory(boolean includeWithoutEndpoints) {
        return http.getForObject(
                appUrl() + "/api/containers?includeWithoutEndpoints=" + includeWithoutEndpoints,
                ContainerInventoryDTO.class);
    }

    private static final String CUSTOM =
            "{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"https://shared.example.com/app\"}";

    @Test
    void emptyNamedVolumeSupportsNonrootSaveAndCustomOnlyInventoryWithoutMixingSources() throws Exception {
        assertThat(app.execInContainer("cat", "/proc/1/status").getStdout())
                .containsPattern("Uid:\\s+65532\\s+65532\\s+65532\\s+65532");
        assertThat(inventory(false).containers()).isEmpty();
        assertThat(inventory(true).containers())
                .allSatisfy(container ->
                        assertThat(container.preferences().selectedEndpoint()).isNull());
        select("mike", "{\"id\":\"none\",\"source\":\"none\"}");
        var saved = select("walker", CUSTOM);
        assertThat(saved.sourceId()).isEqualTo("walker");
        assertThat(saved.containerName()).isEqualTo("shared-app");
        assertThat(saved.preferences().selectedEndpoint())
                .isEqualTo(new SelectedEndpointDTO("custom", "custom", "https://shared.example.com/app"));
        assertThat(inventory(false).containers()).singleElement().satisfies(container -> {
            assertThat(container.sourceId()).isEqualTo("walker");
            assertThat(container.endpoints()).isEmpty();
            assertThat(container.preferences()).isEqualTo(saved.preferences());
        });
        assertThat(inventory(true).containers())
                .filteredOn(c -> c.sourceId().equals("mike"))
                .singleElement()
                .satisfies(c -> assertThat(c.preferences().selectedEndpoint())
                        .isEqualTo(new SelectedEndpointDTO("none", "none", null)));
        assertThat(select("walker", CUSTOM)).isEqualTo(saved);
        assertThat(inventory(false).containers())
                .singleElement()
                .satisfies(c -> assertThat(c.preferences()).isEqualTo(saved.preferences()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "{\"id\":\"none\",\"source\":\"none\"}",
                "{\"id\":\"published-port:8080\",\"source\":\"published-port\"}"
            })
    void replacingCustomDiscardsItsUrlAndRestoresNormalFiltering(String replacement) throws Exception {
        select("walker", CUSTOM);
        var saved = select("walker", replacement);
        if (replacement.equals("null")) {
            assertThat(saved.preferences().selectedEndpoint()).isNull();
        } else {
            assertThat(saved.preferences().selectedEndpoint())
                    .isEqualTo(
                            replacement.contains("published-port")
                                    ? new SelectedEndpointDTO("published-port:8080", "published-port", null)
                                    : new SelectedEndpointDTO("none", "none", null));
        }
        assertThat(inventory(false).containers()).isEmpty();
        assertThat(inventory(true).containers())
                .filteredOn(c -> c.sourceId().equals("walker"))
                .singleElement()
                .satisfies(c -> assertThat(c.preferences()).isEqualTo(saved.preferences()));
        var file = app.execInContainer("cat", "/app/data/preferences.json");
        assertThat(file.getExitCode()).isZero();
        assertThat(file.getStdout()).doesNotContain("https://shared.example.com/app");
    }

    @Test
    void replacingApplicationAndRecreatingContainerRetainsPreferencesInReusedVolume() {
        var saved = select("walker", CUSTOM);
        String previousContainer = app.getContainerId();
        app.stop();
        inventoryFixture("recreated-id");
        startReplacement();
        assertThat(app.getContainerId()).isNotEqualTo(previousContainer);
        assertThat(inventory(false).containers()).singleElement().satisfies(container -> {
            assertThat(container.id()).isEqualTo("recreated-id");
            assertThat(container.sourceId()).isEqualTo("walker");
            assertThat(container.preferences()).isEqualTo(saved.preferences());
        });
    }
}
