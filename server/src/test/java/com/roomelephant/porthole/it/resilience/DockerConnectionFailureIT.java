package com.roomelephant.porthole.it.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.roomelephant.porthole.it.infra.DockerInfrastructure;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;

@Order(1)
class DockerConnectionFailureIT {

    private static final int PORTHOLE_PORT = 9753;

    private static final DockerInfrastructure dockerInfra;
    private static final GenericContainer<?> wireMockContainer;
    private static final String wireMockIp;
    private static final DockerClient dindClient;

    static {
        dockerInfra = new DockerInfrastructure();
        wireMockContainer = dockerInfra.startWireMock();
        wireMockIp = wireMockContainer
                .getContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .values()
                .iterator()
                .next()
                .getIpAddress();
        dindClient = dockerInfra.getDinDClient();

        try {
            dindClient.pullImageCmd("busybox:1.37.0-uclibc").start().awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to pull busybox image", e);
        }

        dockerInfra.ensurePortholeImageBuilt();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            wireMockContainer.stop();
            dockerInfra.close();
        }));
    }

    private String runningContainerId;
    private String portholeBaseUrl;
    private RestTemplate restTemplate;
    private final List<String> lockedVolumeNames = new ArrayList<>();

    @BeforeEach
    void setup() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(response -> false);
    }

    @AfterEach
    void tearDown() {
        if (runningContainerId != null) {
            try {
                dindClient.stopContainerCmd(runningContainerId).withTimeout(5).exec();
            } catch (Exception ignored) {
            }
            try {
                dindClient
                        .removeContainerCmd(runningContainerId)
                        .withForce(true)
                        .exec();
            } catch (Exception ignored) {
            }
            runningContainerId = null;
            portholeBaseUrl = null;
        }
        for (String vol : lockedVolumeNames) {
            try {
                dindClient.removeVolumeCmd(vol).exec();
            } catch (Exception ignored) {
            }
        }
        lockedVolumeNames.clear();
    }

    @Nested
    class WhenSocketMissing {

        @BeforeEach
        void startBrokenPorthole() throws Exception {
            startMissingSocketPorthole();
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
            String containerId = "random-id";
            ResponseEntity<String> response = restTemplate.getForEntity(
                    portholeBaseUrl + "/api/containers/" + containerId + "/version", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }
    }

    @Nested
    class WhenPermissionDenied {

        @BeforeEach
        void startBrokenPorthole() throws Exception {
            startPermissionDeniedPorthole();
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
            String containerId = "random-id";
            ResponseEntity<String> response = restTemplate.getForEntity(
                    portholeBaseUrl + "/api/containers/" + containerId + "/version", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }
    }

    // -------------------------------------------------------------------------
    // Porthole container helpers
    // -------------------------------------------------------------------------

    private void startMissingSocketPorthole() throws Exception {
        String name = "porthole-missing-" + System.nanoTime();
        String id = dindClient
                .createContainerCmd(DockerInfrastructure.PORTHOLE_IT_IMAGE_TAG)
                .withName(name)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(
                                new PortBinding(Ports.Binding.bindPort(PORTHOLE_PORT), ExposedPort.tcp(PORTHOLE_PORT)))
                        .withExtraHosts("wiremock:" + wireMockIp))
                .withExposedPorts(ExposedPort.tcp(PORTHOLE_PORT))
                .withEnv(
                        "PORTHOLE_DOCKER_HOST=unix:///tmp/missing-docker.sock",
                        "REGISTRY_URLS_REGISTRY=http://wiremock:8080/v2/",
                        "REGISTRY_URLS_AUTH=http://wiremock:8080/auth?service=registry.docker.io&scope=repository:",
                        "REGISTRY_URLS_REPOSITORIES=http://wiremock:8080/v2/repositories/",
                        "REGISTRY_CACHE_TTL=1ms",
                        "REGISTRY_CACHE_VERSION_MAX_SIZE=1")
                .exec()
                .getId();

        dindClient.startContainerCmd(id).exec();
        runningContainerId = id;
        portholeBaseUrl = buildBaseUrl();
        waitUntilResponding(portholeBaseUrl);
    }

    private void startPermissionDeniedPorthole() throws Exception {
        String volumeName = "porthole-locked-" + System.nanoTime();
        dindClient.createVolumeCmd().withName(volumeName).exec();
        lockedVolumeNames.add(volumeName);

        // Run busybox inside DinD to create a 0000-permission file on the volume
        String setupId = dindClient
                .createContainerCmd("busybox:1.37.0-uclibc")
                .withCmd("sh", "-c", "touch /data/locked.sock && chmod 000 /data/locked.sock")
                .withHostConfig(HostConfig.newHostConfig().withBinds(new Bind(volumeName, new Volume("/data"))))
                .exec()
                .getId();
        dindClient.startContainerCmd(setupId).exec();
        dindClient.waitContainerCmd(setupId).start().awaitStatusCode();
        dindClient.removeContainerCmd(setupId).withForce(true).exec();

        String name = "porthole-locked-" + System.nanoTime();
        String id = dindClient
                .createContainerCmd(DockerInfrastructure.PORTHOLE_IT_IMAGE_TAG)
                .withName(name)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(
                                new PortBinding(Ports.Binding.bindPort(PORTHOLE_PORT), ExposedPort.tcp(PORTHOLE_PORT)))
                        .withBinds(new Bind(volumeName, new Volume("/docker-sockets")))
                        .withExtraHosts("wiremock:" + wireMockIp))
                .withExposedPorts(ExposedPort.tcp(PORTHOLE_PORT))
                .withEnv(
                        "PORTHOLE_DOCKER_HOST=unix:///docker-sockets/locked.sock",
                        "REGISTRY_URLS_REGISTRY=http://wiremock:8080/v2/",
                        "REGISTRY_URLS_AUTH=http://wiremock:8080/auth?service=registry.docker.io&scope=repository:",
                        "REGISTRY_URLS_REPOSITORIES=http://wiremock:8080/v2/repositories/",
                        "REGISTRY_CACHE_TTL=1ms",
                        "REGISTRY_CACHE_VERSION_MAX_SIZE=1")
                .exec()
                .getId();

        dindClient.startContainerCmd(id).exec();
        runningContainerId = id;
        portholeBaseUrl = buildBaseUrl();
        waitUntilResponding(portholeBaseUrl);
    }

    private String buildBaseUrl() {
        return "http://" + dockerInfra.getDinDHost() + ":" + dockerInfra.getDinDMappedPort9753();
    }

    private static void waitUntilResponding(String baseUrl) throws Exception {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/actuator/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                client.send(request, HttpResponse.BodyHandlers.discarding());
                return; // any response means the server is up
            } catch (Exception ignored) {
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Porthole did not respond within 60s at " + baseUrl);
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    record HealthResponse(String status, Map<String, Object> details) {}
}
