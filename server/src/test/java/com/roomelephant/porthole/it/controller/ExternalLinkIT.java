package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ExternalLinkIT {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final String ROUTER = "{\"name\":\"Router\",\"url\":\"http://192.168.50.1/\",\"stack\":\"base\"}";

    private WireMockServer docker;
    private GenericContainer<?> app;
    private String volume;
    private final RestTemplate http = new RestTemplate();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void start() {
        docker = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        docker.start();
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?_ping")).willReturn(ok("OK")));
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?containers/json")).willReturn(okJson("[]")));
        volume = "porthole-links-it-" + UUID.randomUUID();
        DockerClientFactory.instance()
                .client()
                .createVolumeCmd()
                .withName(volume)
                .exec();
        startApplication();
    }

    @AfterEach
    void stop() {
        if (app != null) app.stop();
        if (volume != null)
            DockerClientFactory.instance().client().removeVolumeCmd(volume).exec();
        if (docker != null && docker.isRunning()) docker.stop();
    }

    private void startApplication() {
        app = PortholeContainer.withCustomSocket("/tmp/unused.sock", docker.port());
        app.withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_ID", "walker")
                .withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_NAME", "Walker")
                .withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_HOST", "tcp://host.docker.internal:" + docker.port());
        app.withCreateContainerCmdModifier(cmd -> {
            var binds = new ArrayList<Bind>();
            if (cmd.getHostConfig().getBinds() != null)
                binds.addAll(Arrays.asList(cmd.getHostConfig().getBinds()));
            binds.add(new Bind(volume, new Volume("/app/data")));
            cmd.getHostConfig().withBinds(binds);
        });
        app.start();
    }

    private String url(String path) {
        return "http://localhost:" + app.getMappedPort(PortholeContainer.PORTHOLE_PORT) + path;
    }

    private static HttpEntity<String> json(String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private JsonNode links() {
        return mapper.readTree(http.getForObject(url("/api/links"), String.class));
    }

    private String create() {
        var response = http.postForEntity(url("/api/links"), json(ROUTER), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(response.getBody()).path("id").asString();
    }

    private ResponseEntity<String> upload(String id) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(PNG) {
            @Override
            public String getFilename() {
                return "logo.png";
            }
        });
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity(
                url("/api/links/" + id + "/icon/upload"), new HttpEntity<>(parts, headers), String.class);
    }

    @Test
    void createListUpdateDelete() {
        assertThat(links()).isEmpty();

        String id = create();
        assertThat(id).matches("[0-9a-f]{32}");
        JsonNode created = links().get(0);
        assertThat(created.path("name").asString()).isEqualTo("Router");
        assertThat(created.path("url").asString()).isEqualTo("http://192.168.50.1/");
        assertThat(created.path("stack").asString()).isEqualTo("base");
        assertThat(created.path("iconUrl").isNull()).isTrue();

        var updated = http.exchange(
                url("/api/links/" + id),
                HttpMethod.PUT,
                json("{\"name\":\"Gateway\",\"url\":\"https://gw/\",\"stack\":null}"),
                String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(links().get(0).path("name").asString()).isEqualTo("Gateway");
        assertThat(links().get(0).path("stack").isNull()).isTrue();

        http.delete(url("/api/links/" + id));
        assertThat(links()).isEmpty();
    }

    @Test
    void rejectsJavascriptUrl() {
        String body = "{\"name\":\"Bad\",\"url\":\"javascript:alert(1)\"}";

        assertThatThrownBy(() -> http.postForEntity(url("/api/links"), json(body), String.class))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        assertThat(links()).isEmpty();
    }

    @Test
    void unknownLinkIs404() {
        String missing = "0123456789abcdef0123456789abcdef";

        assertThatThrownBy(() -> http.delete(url("/api/links/" + missing)))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void libraryAndUploadedIconsResolve() {
        String id = create();

        http.exchange(url("/api/links/" + id + "/icon"), HttpMethod.PUT, json("{\"name\":\"router\"}"), String.class);
        assertThat(links().get(0).path("iconUrl").asString()).endsWith("/router.webp");

        assertThat(upload(id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String iconUrl = links().get(0).path("iconUrl").asString();
        assertThat(iconUrl).matches("/api/icons/[0-9a-f]{32}\\.png");
        var image = http.getForEntity(url(iconUrl), byte[].class);
        assertThat(image.getBody()).isEqualTo(PNG);
        assertThat(image.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
    }

    @Test
    void deletingALinkRemovesItsUploadedIcon() {
        String id = create();
        upload(id);
        String iconUrl = links().get(0).path("iconUrl").asString();

        http.delete(url("/api/links/" + id));

        assertThatThrownBy(() -> http.getForEntity(url(iconUrl), byte[].class))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void linksAndIconsSurviveReplacement() {
        String id = create();
        upload(id);
        String iconUrl = links().get(0).path("iconUrl").asString();

        app.stop();
        startApplication();

        assertThat(links().get(0).path("id").asString()).isEqualTo(id);
        assertThat(http.getForEntity(url(iconUrl), byte[].class).getBody()).isEqualTo(PNG);
    }

    @Test
    void linksServedWhileDockerUnreachable() {
        String id = create();

        docker.stop();

        assertThat(links().get(0).path("id").asString()).isEqualTo(id);
    }

    @Test
    void containersResponseStaysSeparate() {
        create();

        JsonNode inventory = mapper.readTree(http.getForObject(url("/api/containers"), String.class));

        assertThat(inventory.has("links")).isFalse();
        assertThat(inventory.path("sources")).hasSize(1);
        assertThat(inventory.path("sources").get(0).path("id").asString()).isEqualTo("walker");
    }
}
