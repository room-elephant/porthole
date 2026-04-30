package com.roomelephant.porthole.it.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Order(1)
class DockerConnectionFailureIT {

    private static File socketFile;

    @LocalServerPort
    protected int port;

    private RestTemplate restTemplate;

    @BeforeEach
    void setup() {
        this.restTemplate = new RestTemplate();
        this.restTemplate.setErrorHandler(response -> false);
    }

    @AfterEach
    void cleanup() {
        if (socketFile != null && socketFile.exists()) {
            socketFile.delete();
        }
    }

    @DynamicPropertySource
    static void configureProperties(@NotNull DynamicPropertyRegistry registry) {
        try {
            socketFile = File.createTempFile("docker-failure-test", ".sock");

            registry.add("porthole.docker.host", () -> "unix://" + socketFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to setup socket file path", e);
        }
    }

    @Test
    void ShouldReturnDownWhenSocketMissingOnHealth() {
        ResponseEntity<HealthResponse> response =
                restTemplate.getForEntity(createURLWithPort("/actuator/health/docker"), HealthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        HealthResponse health = response.getBody();
        assertThat(health).isNotNull();
        assertThat(health.status()).isEqualTo("DOWN");
        assertThat(health.details()).containsEntry("Error connecting to docker", "No such file or directory");
    }

    @Test
    void shouldReturn502WhenSocketMissingOnContainers() {
        ResponseEntity<String> response = restTemplate.getForEntity(createURLWithPort("/api/containers"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldReturn502WhenSocketMissingOnVersion() {
        String containerId = "random-id";
        ResponseEntity<String> response = restTemplate.getForEntity(
                createURLWithPort("/api/containers/" + containerId + "/version"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldReturnDownWhenPermissionDeniedOnHealth() throws IOException {
        ensureSocketPermissionDenied();

        ResponseEntity<HealthResponse> response =
                restTemplate.getForEntity(createURLWithPort("/actuator/health/docker"), HealthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        HealthResponse health = response.getBody();
        assertThat(health).isNotNull();
        assertThat(health.status()).isEqualTo("DOWN");
        assertThat(health.details()).containsEntry("Error connecting to docker", "Permission denied");
    }

    @Test
    void shouldReturn502WhenPermissionDeniedOnContainers() throws IOException {

        ensureSocketPermissionDenied();

        ResponseEntity<String> response = restTemplate.getForEntity(createURLWithPort("/api/containers"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldReturn502WhenPermissionDeniedOnVersion() throws IOException {

        ensureSocketPermissionDenied();

        String containerId = "random-id";
        ResponseEntity<String> response = restTemplate.getForEntity(
                createURLWithPort("/api/containers/" + containerId + "/version"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    private @NotNull String createURLWithPort(String uri) {
        return "http://localhost:" + port + uri;
    }

    private void ensureSocketPermissionDenied() throws IOException {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketFile.toPath());
        ServerSocketChannel serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        serverChannel.bind(address);
        serverChannel.close();

        Set<PosixFilePermission> perms = new HashSet<>();
        Files.setPosixFilePermissions(socketFile.toPath(), perms);
    }

    record HealthResponse(String status, Map<String, Object> details) {}
}
