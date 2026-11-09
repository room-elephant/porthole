package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EntriesPersistenceIT {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 4, 5, 6};
    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

    private WireMockServer docker;
    private GenericContainer<?> app;
    private String volume;
    private final RestTemplate http = new RestTemplate(new JdkClientHttpRequestFactory());
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void start() {
        docker = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        docker.start();
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?_ping")).willReturn(ok("OK")));
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?containers/json")).willReturn(okJson("""
                        [{"Id":"app-id","Names":["/shared-app"],"Image":"local-app:latest",
                        "ImageID":"local-image","State":"running","Status":"Up","Labels":{},"Ports":[]}]
                        """)));
        volume = "porthole-state-it-" + UUID.randomUUID();
        DockerClientFactory.instance()
                .client()
                .createVolumeCmd()
                .withName(volume)
                .exec();
        app = application();
        app.start();
    }

    @AfterEach
    void stop() {
        if (app != null) app.stop();
        if (volume != null)
            DockerClientFactory.instance().client().removeVolumeCmd(volume).exec();
        if (docker != null) docker.stop();
    }

    private GenericContainer<?> application() {
        GenericContainer<?> container = PortholeContainer.withCustomSocket("/tmp/unused.sock", docker.port());
        container
                .withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_ID", "walker")
                .withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_NAME", "Walker")
                .withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_HOST", "tcp://host.docker.internal:" + docker.port());
        container.withCreateContainerCmdModifier(cmd -> {
            var binds = new ArrayList<Bind>();
            if (cmd.getHostConfig().getBinds() != null)
                binds.addAll(Arrays.asList(cmd.getHostConfig().getBinds()));
            binds.add(new Bind(volume, new Volume("/app/data")));
            cmd.getHostConfig().withBinds(binds);
        });
        return container;
    }

    private String url(String path) {
        return "http://localhost:" + app.getMappedPort(PortholeContainer.PORTHOLE_PORT) + path;
    }

    private static HttpEntity<Object> body(Object body, MediaType type) {
        var headers = new HttpHeaders();
        headers.setContentType(type);
        return new HttpEntity<>(body, headers);
    }

    private String id(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(response.getBody()).path("id").asString();
    }

    private JsonNode container() {
        return mapper.readTree(http.getForObject(url("/api/containers"), String.class))
                .path("containers")
                .get(0);
    }

    @Test
    void entriesAndIconsSurviveRestart() throws Exception {
        String icon = id(http.postForEntity(url("/api/icons"), body(PNG, MediaType.IMAGE_PNG), String.class));
        String patch = "{\"icon\":{\"upload\":\"" + icon + "\"},\"endpoint\":{\"url\":\"https://app.example/\"}}";
        http.exchange(
                url("/api/entries/container:walker:shared-app"),
                HttpMethod.PATCH,
                body(patch, MERGE_PATCH),
                String.class);
        String link = id(http.postForEntity(
                url("/api/entries"),
                body(
                        "{\"name\":\"Router\",\"url\":\"http://192.168.50.1/\",\"stack\":\"base\"}",
                        MediaType.APPLICATION_JSON),
                String.class));

        app.stop();
        app = application();
        app.start();

        assertThat(container().path("entryId").asString()).isEqualTo("container:walker:shared-app");
        assertThat(container().path("iconUrl").asString()).isEqualTo("/api/icons/" + icon);
        assertThat(container().path("endpoints").get(0).path("url").asString()).isEqualTo("https://app.example/");
        JsonNode links = mapper.readTree(http.getForObject(url("/api/links"), String.class));
        assertThat(links.get(0).path("id").asString()).isEqualTo(link);
        assertThat(links.get(0).path("stack").asString()).isEqualTo("base");
        assertThat(http.getForEntity(url("/api/icons/" + icon), byte[].class).getBody())
                .isEqualTo(PNG);
        String state = app.execInContainer("cat", "/app/data/state.json").getStdout();
        assertThat(mapper.readTree(state).path("schemaVersion").asInt()).isEqualTo(1);
    }

    @Test
    void newerSchemaVersionFailsStartup() {
        app.copyFileToContainer(Transferable.of("{\"schemaVersion\":2,\"entries\":[]}"), "/app/data/state.json");
        app.stop();

        ToStringConsumer logs = new ToStringConsumer();
        app = application();
        app.withLogConsumer(logs)
                .waitingFor(Wait.forHttp("/actuator/health/liveness")
                        .forPort(PortholeContainer.PORTHOLE_PORT)
                        .withStartupTimeout(Duration.ofSeconds(20)));

        assertThatThrownBy(app::start).isInstanceOf(RuntimeException.class);
        assertThat(logs.toUtf8String()).contains("schemaVersion 2");
    }
}
