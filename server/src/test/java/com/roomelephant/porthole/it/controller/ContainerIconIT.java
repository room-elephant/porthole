package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ContainerIconIT {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final String LIBRARY_URL =
            "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp/coredns.webp";
    private static final String CUSTOM =
            "{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"https://shared.example.com/app\"}";

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
        inventoryFixture("original-id");
        volume = "porthole-icons-it-" + UUID.randomUUID();
        DockerClientFactory.instance()
                .client()
                .createVolumeCmd()
                .withName(volume)
                .exec();
        startReplacement();
    }

    @AfterEach
    void stop() {
        if (app != null) app.stop();
        if (volume != null)
            DockerClientFactory.instance().client().removeVolumeCmd(volume).exec();
        if (docker != null) docker.stop();
    }

    private void inventoryFixture(String id) {
        docker.stubFor(get(urlPathMatching("/(?:v[0-9.]+/)?containers/json")).willReturn(okJson("""
                        [{"Id":"%s","Names":["/shared-app"],"Image":"local-app:latest",
                        "ImageID":"local-image","State":"running","Status":"Up","Labels":{},"Ports":[]}]
                        """.formatted(id))));
    }

    private void startReplacement() {
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

    private void replaceApplication() {
        app.stop();
        inventoryFixture("recreated-id");
        startReplacement();
    }

    private String appUrl() {
        return "http://localhost:" + app.getMappedPort(PortholeContainer.PORTHOLE_PORT);
    }

    private JsonNode container() {
        String body = http.getForObject(appUrl() + "/api/containers?includeWithoutEndpoints=true", String.class);
        return mapper.readTree(body).path("containers").get(0);
    }

    private String iconUrl() {
        return container().path("iconUrl").asString();
    }

    private void pickLibraryIcon() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"sourceId\":\"walker\",\"containerName\":\"shared-app\",\"name\":\"coredns\"}";
        var response = http.exchange(
                appUrl() + "/api/containers/icon", HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private ResponseEntity<String> upload(byte[] bytes) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("sourceId", "walker");
        parts.add("containerName", "shared-app");
        parts.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "logo.png";
            }
        });
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity(
                appUrl() + "/api/containers/icon/upload", new HttpEntity<>(parts, headers), String.class);
    }

    @Test
    void pickingLibraryIconChangesIconUrlOnInventory() {
        pickLibraryIcon();
        assertThat(iconUrl()).isEqualTo(LIBRARY_URL);
    }

    @Test
    void uploadIsServedWithContentTypeAndNosniffAndUsedAsIconUrl() {
        assertThat(upload(PNG).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String url = iconUrl();
        assertThat(url).matches("/api/icons/[0-9a-f]{32}\\.png");

        var image = http.getForEntity(appUrl() + url, byte[].class);
        assertThat(image.getBody()).isEqualTo(PNG);
        assertThat(image.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(image.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(image.getHeaders().getCacheControl()).contains("immutable");
    }

    @Test
    void replacingUploadWithLibraryIconRemovesTheFile() {
        upload(PNG);
        String url = iconUrl();
        pickLibraryIcon();
        assertThatThrownBy(() -> http.getForEntity(appUrl() + url, byte[].class))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void disguisedFileIsRejectedWith400() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> upload(svg)).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void uploadOverOneMegabyteIsRejectedWith413() {
        byte[] oversized = Arrays.copyOf(PNG, 1_048_577);
        assertThatThrownBy(() -> upload(oversized))
                .isInstanceOfSatisfying(
                        HttpClientErrorException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
    }

    @Test
    void uploadOfExactlyOneMegabyteIsAccepted() {
        assertThat(upload(Arrays.copyOf(PNG, 1_048_576)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void iconSurvivesReplacingApplicationAndRecreatingContainer() {
        upload(PNG);
        String url = iconUrl();
        replaceApplication();
        assertThat(container().path("id").asString()).isEqualTo("recreated-id");
        assertThat(iconUrl()).isEqualTo(url);
        assertThat(http.getForEntity(appUrl() + url, byte[].class).getBody()).isEqualTo(PNG);
    }

    @Test
    void savingUrlKeepsIconAndSavingIconKeepsUrl() {
        pickLibraryIcon();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String selection = "{\"sourceId\":\"walker\",\"containerName\":\"shared-app\",\"selectedEndpoint\":%s}";
        http.exchange(
                appUrl() + "/api/containers/selected-url",
                HttpMethod.PUT,
                new HttpEntity<>(selection.formatted(CUSTOM), headers),
                String.class);
        assertThat(iconUrl()).isEqualTo(LIBRARY_URL);

        pickLibraryIcon();
        assertThat(container().path("endpoints").get(0).path("url").asString())
                .isEqualTo("https://shared.example.com/app");

        http.exchange(
                appUrl() + "/api/containers/selected-url",
                HttpMethod.PUT,
                new HttpEntity<>(selection.formatted("null"), headers),
                String.class);
        assertThat(iconUrl()).isEqualTo(LIBRARY_URL);
    }

    @Test
    void unreferencedFileIsRemovedAtStartup() {
        upload(PNG);
        String kept = iconUrl();
        String orphan = "ffffffffffffffffffffffffffffffff.png";
        app.copyFileToContainer(Transferable.of(PNG), "/app/data/icons/" + orphan);
        assertThat(http.getForEntity(appUrl() + "/api/icons/" + orphan, byte[].class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        replaceApplication();

        assertThatThrownBy(() -> http.getForEntity(appUrl() + "/api/icons/" + orphan, byte[].class))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThat(http.getForEntity(appUrl() + kept, byte[].class).getBody()).isEqualTo(PNG);
    }
}
