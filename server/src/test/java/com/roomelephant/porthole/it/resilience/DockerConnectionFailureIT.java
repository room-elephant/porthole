package com.roomelephant.porthole.it.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

@Order(1)
class DockerConnectionFailureIT {

    private static final WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(wireMockServer::stop));
    }

    private GenericContainer<?> porthole;
    private String portholeBaseUrl;
    private RestTemplate restTemplate;

    @BeforeEach
    void setup() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(response -> false);
    }

    @AfterEach
    void tearDown() {
        if (porthole != null && porthole.isRunning()) {
            porthole.stop();
            porthole = null;
        }
        portholeBaseUrl = null;
    }

    private String baseUrlFor(GenericContainer<?> container) {
        return "http://localhost:" + container.getMappedPort(PortholeContainer.PORTHOLE_PORT);
    }

    @Nested
    class WhenSocketMissing {

        @BeforeEach
        void startBrokenPorthole() {
            porthole = PortholeContainer.withCustomSocket(
                    "/tmp/missing-docker.sock", wireMockServer.port());
            porthole.start();
            portholeBaseUrl = baseUrlFor(porthole);
        }

        @Test
        void ShouldReturnDownWhenSocketMissingOnHealth() {
            ResponseEntity<HealthResponse> response =
                    restTemplate.getForEntity(portholeBaseUrl + "/actuator/health/docker", HealthResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            HealthResponse health = response.getBody();
            assertThat(health).isNotNull();
            assertThat(health.status()).isEqualTo("DOWN");
            assertThat(health.details()).containsEntry("Error connecting to docker", "No such file or directory");
        }

        @Test
        void shouldReturn502WhenSocketMissingOnContainers() {
            ResponseEntity<String> response =
                    restTemplate.getForEntity(portholeBaseUrl + "/api/containers", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }

        @Test
        void shouldReturn502WhenSocketMissingOnVersion() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    portholeBaseUrl + "/api/containers/random-id/version", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }
    }

    @Nested
    class WhenPermissionDenied {

        private Path lockedSocketPath;

        @BeforeEach
        void startBrokenPorthole() throws Exception {
            lockedSocketPath = Files.createTempFile("locked-docker", ".sock");
            Files.setPosixFilePermissions(lockedSocketPath, Set.of());

            porthole = PortholeContainer.withCustomSocket(
                            "/docker-sockets/locked.sock", wireMockServer.port())
                    .withFileSystemBind(
                            lockedSocketPath.toString(),
                            "/docker-sockets/locked.sock",
                            BindMode.READ_WRITE);
            porthole.start();
            portholeBaseUrl = baseUrlFor(porthole);
        }

        @AfterEach
        void deleteLocked() throws Exception {
            if (lockedSocketPath != null) {
                Files.deleteIfExists(lockedSocketPath);
            }
        }

        @Test
        void shouldReturnDownWhenPermissionDeniedOnHealth() {
            ResponseEntity<HealthResponse> response =
                    restTemplate.getForEntity(portholeBaseUrl + "/actuator/health/docker", HealthResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            HealthResponse health = response.getBody();
            assertThat(health).isNotNull();
            assertThat(health.status()).isEqualTo("DOWN");
            assertThat(health.details()).containsEntry("Error connecting to docker", "Permission denied");
        }

        @Test
        void shouldReturn502WhenPermissionDeniedOnContainers() {
            ResponseEntity<String> response =
                    restTemplate.getForEntity(portholeBaseUrl + "/api/containers", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }

        @Test
        void shouldReturn502WhenPermissionDeniedOnVersion() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    portholeBaseUrl + "/api/containers/random-id/version", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }
    }

    record HealthResponse(String status, Map<String, Object> details) {}
}
