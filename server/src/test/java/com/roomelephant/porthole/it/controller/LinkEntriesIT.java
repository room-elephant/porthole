package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LinkEntriesIT {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final String ROUTER = "{\"name\":\"Router\",\"url\":\"http://192.168.50.1/\",\"stack\":\"base\"}";
    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

    private WireMockServer docker;
    private GenericContainer<?> app;
    private final RestTemplate http = new RestTemplate(new JdkClientHttpRequestFactory());
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void start() {
        docker = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        docker.start();
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?_ping")).willReturn(ok("OK")));
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?containers/json")).willReturn(okJson("[]")));
        app = PortholeContainer.withCustomSocket("/tmp/unused.sock", docker.port());
        app.withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_ID", "walker")
                .withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_NAME", "Walker")
                .withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_HOST", "tcp://host.docker.internal:" + docker.port());
        app.start();
    }

    @AfterEach
    void stop() {
        if (app != null) app.stop();
        if (docker != null && docker.isRunning()) docker.stop();
    }

    private String url(String path) {
        return "http://localhost:" + app.getMappedPort(PortholeContainer.PORTHOLE_PORT) + path;
    }

    private static HttpEntity<Object> body(Object body, MediaType type) {
        var headers = new HttpHeaders();
        headers.setContentType(type);
        return new HttpEntity<>(body, headers);
    }

    private JsonNode links() {
        return mapper.readTree(http.getForObject(url("/api/links"), String.class));
    }

    private String create() {
        var response = http.postForEntity(url("/api/entries"), body(ROUTER, MediaType.APPLICATION_JSON), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(response.getBody()).path("id").asString();
    }

    private ResponseEntity<String> patch(String id, String patch) {
        return http.exchange(url("/api/entries/" + id), HttpMethod.PATCH, body(patch, MERGE_PATCH), String.class);
    }

    private String upload() {
        var response = http.postForEntity(url("/api/icons"), body(PNG, MediaType.IMAGE_PNG), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(response.getBody()).path("id").asString();
    }

    @Test
    void createListUpdateDelete() {
        assertThat(links()).isEmpty();

        String id = create();
        assertThat(id).matches("link:[0-9a-f]{32}");
        JsonNode created = links().get(0);
        assertThat(created.path("id").asString()).isEqualTo(id);
        assertThat(created.path("name").asString()).isEqualTo("Router");
        assertThat(created.path("url").asString()).isEqualTo("http://192.168.50.1/");
        assertThat(created.path("stack").asString()).isEqualTo("base");
        assertThat(created.path("iconUrl").isNull()).isTrue();

        var updated = patch(id, "{\"name\":\"Gateway\",\"endpoint\":{\"url\":\"https://gw/\"},\"stack\":null}");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(links().get(0).path("name").asString()).isEqualTo("Gateway");
        assertThat(links().get(0).path("url").asString()).isEqualTo("https://gw/");
        assertThat(links().get(0).path("stack").isNull()).isTrue();

        http.delete(url("/api/entries/" + id));
        assertThat(links()).isEmpty();
    }

    @Test
    void createWithIconInOneRequest() {
        String icon = upload();
        String request = "{\"name\":\"NAS\",\"url\":\"http://nas/\",\"icon\":{\"upload\":\"" + icon + "\"}}";

        http.postForEntity(url("/api/entries"), body(request, MediaType.APPLICATION_JSON), String.class);

        assertThat(links().get(0).path("iconUrl").asString()).isEqualTo("/api/icons/" + icon);
    }

    @Test
    void rejectsJavascriptUrl() {
        String request = "{\"name\":\"Bad\",\"url\":\"javascript:alert(1)\"}";

        assertThatThrownBy(() -> http.postForEntity(
                        url("/api/entries"), body(request, MediaType.APPLICATION_JSON), String.class))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        assertThat(links()).isEmpty();
    }

    @Test
    void removingALinksNameOrUrlIsRejected() {
        String id = create();

        assertThatThrownBy(() -> patch(id, "{\"name\":null}")).isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> patch(id, "{\"endpoint\":null}"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThat(links().get(0).path("name").asString()).isEqualTo("Router");
    }

    @Test
    void unknownLinkIs404() {
        String missing = "link:0123456789abcdef0123456789abcdef";

        assertThatThrownBy(() -> http.delete(url("/api/entries/" + missing)))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> patch(missing, "{\"name\":\"x\"}"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void containerEntriesCannotBeDeleted() {
        assertThatThrownBy(() -> http.delete(url("/api/entries/container:walker:app")))
                .isInstanceOfSatisfying(HttpClientErrorException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
                    assertThat(error.getResponseHeaders().getFirst("Allow")).isEqualTo("PATCH");
                });
    }

    @Test
    void libraryAndUploadedIconsResolve() {
        String id = create();

        patch(id, "{\"icon\":{\"library\":\"router\"}}");
        assertThat(links().get(0).path("iconUrl").asString()).endsWith("/router.webp");

        String icon = upload();
        patch(id, "{\"icon\":{\"upload\":\"" + icon + "\"}}");
        String iconUrl = links().get(0).path("iconUrl").asString();
        assertThat(iconUrl).isEqualTo("/api/icons/" + icon).matches("/api/icons/[0-9a-f]{64}\\.png");
        var image = http.getForEntity(url(iconUrl), byte[].class);
        assertThat(image.getBody()).isEqualTo(PNG);
        assertThat(image.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);

        patch(id, "{\"icon\":null}");
        assertThat(links().get(0).path("iconUrl").isNull()).isTrue();
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
