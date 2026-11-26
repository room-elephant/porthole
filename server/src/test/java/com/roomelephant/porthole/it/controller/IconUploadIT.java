package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class IconUploadIT {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final String ENTRY = "/api/entries/container:walker:shared-app";
    private static final String LIBRARY_URL =
            "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp/coredns.webp";
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
        inventoryFixture("original-id");
        volume = "porthole-icons-it-" + UUID.randomUUID();
        DockerClientFactory.instance()
                .client()
                .createVolumeCmd()
                .withName(volume)
                .exec();
        startApplication("1h");
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

    private void startApplication(String unreferencedIconAge) {
        app = PortholeContainer.withCustomSocket("/tmp/unused.sock", docker.port());
        app.withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_ID", "walker")
                .withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_NAME", "Walker")
                .withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_HOST", "tcp://host.docker.internal:" + docker.port())
                .withEnv("PORTHOLE_DATA_UNREFERENCEDICONAGE", unreferencedIconAge);
        app.withCreateContainerCmdModifier(cmd -> {
            var binds = new ArrayList<Bind>();
            if (cmd.getHostConfig().getBinds() != null)
                binds.addAll(Arrays.asList(cmd.getHostConfig().getBinds()));
            binds.add(new Bind(volume, new Volume("/app/data")));
            cmd.getHostConfig().withBinds(binds);
        });
        app.start();
    }

    private void replaceApplication(String unreferencedIconAge) {
        app.stop();
        inventoryFixture("recreated-id");
        startApplication(unreferencedIconAge);
    }

    private String appUrl() {
        return "http://localhost:" + app.getMappedPort(PortholeContainer.PORTHOLE_PORT);
    }

    private JsonNode container() {
        String body = http.getForObject(appUrl() + "/api/dashboard", String.class);
        return mapper.readTree(body).path("entries").get(0);
    }

    private String iconUrl() {
        return container().path("icon").path("url").asString();
    }

    private void patch(String patch) {
        var headers = new HttpHeaders();
        headers.setContentType(MERGE_PATCH);
        var response =
                http.exchange(appUrl() + ENTRY, HttpMethod.PATCH, new HttpEntity<>(patch, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> post(byte[] bytes) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return http.postForEntity(appUrl() + "/api/icons", new HttpEntity<>(bytes, headers), String.class);
    }

    private String upload(byte[] bytes) {
        ResponseEntity<String> response = post(bytes);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(response.getBody()).path("id").asString();
    }

    private void useUpload(String id) {
        patch("{\"icon\":{\"upload\":\"" + id + "\"}}");
    }

    @Test
    void pickingLibraryIconChangesIconUrlOnInventory() {
        patch("{\"icon\":{\"library\":\"coredns\"}}");
        assertThat(iconUrl()).isEqualTo(LIBRARY_URL);
    }

    @Test
    void uploadIsServedWithContentTypeAndNosniffAndUsedAsIconUrl() {
        String id = upload(PNG);
        assertThat(id).matches("[0-9a-f]{64}\\.png");
        useUpload(id);
        assertThat(iconUrl()).isEqualTo("/api/icons/" + id);

        var image = http.getForEntity(appUrl() + iconUrl(), byte[].class);
        assertThat(image.getBody()).isEqualTo(PNG);
        assertThat(image.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(image.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(image.getHeaders().getCacheControl()).contains("immutable");
    }

    @Test
    void sameImageGetsTheSameId() {
        assertThat(upload(PNG)).isEqualTo(upload(PNG));
    }

    @Test
    void savingAnIconThatWasNeverUploadedIsRejected() {
        String missing = "f".repeat(64) + ".png";

        assertThatThrownBy(() -> useUpload(missing)).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void disguisedFileIsRejectedWith400() {
        byte[] text = "<html>not an image</html>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> post(text)).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void malformedSvgIsRejectedWith400() {
        byte[] malformed = "<svg xmlns=\"http://www.w3.org/2000/svg\"><circle".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> post(malformed)).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void uploadedSvgIsServedWithoutItsScriptsAndSandboxed() {
        String id = upload("<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"><script>alert(2)</script>"
                .concat("<circle r=\"1\"/></svg>")
                .getBytes(StandardCharsets.UTF_8));
        assertThat(id).endsWith(".svg");

        ResponseEntity<byte[]> served = http.getForEntity(appUrl() + "/api/icons/" + id, byte[].class);

        assertThat(served.getHeaders().getContentType()).hasToString("image/svg+xml");
        assertThat(served.getHeaders().getFirst("Content-Security-Policy")).contains("sandbox");
        assertThat(new String(served.getBody(), StandardCharsets.UTF_8))
                .contains("<circle")
                .doesNotContain("alert");
    }

    @Test
    void uploadOverOneMegabyteIsRejectedWith413() {
        byte[] oversized = Arrays.copyOf(PNG, 1_048_577);
        assertThatThrownBy(() -> post(oversized))
                .isInstanceOfSatisfying(
                        HttpClientErrorException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
    }

    @Test
    void chunkedUploadOverOneMegabyteIsRejectedWith413() throws Exception {
        byte[] oversized = Arrays.copyOf(PNG, 2 * 1_048_576);
        HttpRequest request = HttpRequest.newBuilder(URI.create(appUrl() + "/api/icons"))
                .header("Content-Type", "image/png")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(oversized)))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(response.request().headers().firstValue("Content-Length"))
                    .isEmpty();
        }
    }

    @Test
    void uploadOfExactlyOneMegabyteIsAccepted() {
        assertThat(post(Arrays.copyOf(PNG, 1_048_576)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void iconSurvivesReplacingApplicationAndRecreatingContainer() {
        useUpload(upload(PNG));
        String url = iconUrl();

        replaceApplication("1h");

        assertThat(container().path("id").asString()).isEqualTo("container:walker:shared-app");
        assertThat(iconUrl()).isEqualTo(url);
        assertThat(http.getForEntity(appUrl() + url, byte[].class).getBody()).isEqualTo(PNG);
    }

    @Test
    void savingUrlKeepsIconAndSavingIconKeepsUrl() {
        patch("{\"icon\":{\"library\":\"coredns\"}}");
        patch("{\"endpoint\":{\"url\":\"https://shared.example.com/app\"}}");
        assertThat(iconUrl()).isEqualTo(LIBRARY_URL);

        patch("{\"icon\":{\"library\":\"coredns\"}}");
        assertThat(container().path("endpoints").get(0).path("url").asString())
                .isEqualTo("https://shared.example.com/app");

        patch("{\"endpoint\":null}");
        assertThat(iconUrl()).isEqualTo(LIBRARY_URL);
    }

    @Test
    void unreferencedFilesAreSweptAtStartupAndReferencedOnesKept() {
        String kept = upload(PNG);
        useUpload(kept);
        String replaced = upload(Arrays.copyOf(PNG, 20));
        String orphan = "f".repeat(64) + ".png";
        app.copyFileToContainer(Transferable.of(PNG), "/app/data/icons/" + orphan);
        assertThat(http.getForEntity(appUrl() + "/api/icons/" + orphan, byte[].class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        replaceApplication("0s");

        // The sweep starts once the application is ready, alongside the first requests.
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThatThrownBy(
                                () -> http.getForEntity(appUrl() + "/api/icons/" + orphan, byte[].class))
                        .isInstanceOf(HttpClientErrorException.NotFound.class));
        assertThatThrownBy(() -> http.getForEntity(appUrl() + "/api/icons/" + replaced, byte[].class))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThat(http.getForEntity(appUrl() + "/api/icons/" + kept, byte[].class)
                        .getBody())
                .isEqualTo(PNG);
    }
}
