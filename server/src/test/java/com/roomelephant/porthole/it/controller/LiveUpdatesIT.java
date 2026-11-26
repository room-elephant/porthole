package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Browsers learn about dashboard changes from GET /api/events and start version checks with a POST. */
class LiveUpdatesIT {
    private static final String ROUTER = "{\"name\":\"Router\",\"url\":\"http://192.168.50.1/\"}";

    private WireMockServer docker;
    private GenericContainer<?> app;
    private Thread reader;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
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
        if (reader != null) reader.interrupt();
        if (app != null) app.stop();
        if (docker != null && docker.isRunning()) docker.stop();
    }

    private String url(String path) {
        return "http://localhost:" + app.getMappedPort(PortholeContainer.PORTHOLE_PORT) + path;
    }

    private JsonNode dashboard() {
        return mapper.readTree(http.getForObject(url("/api/dashboard"), String.class));
    }

    /** Opens the stream and copies its lines to {@link #lines} on a background thread. */
    private void listen() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/events")))
                .header("Accept", "text/event-stream")
                .build();
        HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/event-stream"));
        reader = Thread.ofVirtual().start(() -> {
            try {
                response.body().forEach(lines::add);
            } catch (java.io.UncheckedIOException _) {
                // The test ended and interrupted the read.
            }
        });
    }

    /**
     * The revision of the first {@code changed} event after {@code after} within the timeout. A stream can repeat a
     * revision: a change made just before it opened is counted in its first event and delivered again once coalesced.
     */
    private long revisionAfter(long after, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long revision = nextRevision(Duration.ofNanos(deadline - System.nanoTime()));
            if (revision > after) return revision;
        }
    }

    /** The revision of the next {@code changed} event within the timeout. */
    private long nextRevision(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean changed = false;
        while (true) {
            String line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            assertThat(line).as("an event before the timeout").isNotNull();
            if (line.equals("event:changed")) changed = true;
            else if (changed && line.startsWith("data:"))
                return mapper.readTree(line.substring("data:".length()))
                        .path("revision")
                        .asLong();
        }
    }

    @Test
    void signalsEachSavedChange() throws Exception {
        listen();
        long opened = nextRevision(Duration.ofSeconds(5));
        await().atMost(Duration.ofSeconds(5)).until(() -> lines.contains("retry:5000"));

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity(url("/api/entries"), new HttpEntity<>(ROUTER, headers), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        long changed = revisionAfter(opened, Duration.ofSeconds(5));
        assertThat(dashboard().path("revision").asLong()).isGreaterThanOrEqualTo(changed);
    }

    @Test
    void versionChecksRunInTheBackground() {
        assertThat(dashboard().path("versionChecks").path("lastCompletedAt").isNull())
                .isTrue();

        assertThat(http.postForEntity(url("/api/version-checks"), null, String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode checks = dashboard().path("versionChecks");
            assertThat(checks.path("running").isBoolean()).isTrue();
            assertThat(checks.path("running").asBoolean()).isFalse();
            assertThat(checks.path("lastCompletedAt").isString()).isTrue();
        });
    }
}
