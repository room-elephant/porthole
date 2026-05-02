package com.roomelephant.porthole.it.infra;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Set;
import lombok.SneakyThrows;

public class PortholeContainer {

    private static final String IMAGE_TAG = "porthole-it:latest";
    private static final int PORTHOLE_PORT = 9753;

    private final DockerInfrastructure infra;
    private final String wireMockIp;
    private String containerId;

    public PortholeContainer(DockerInfrastructure infra, String wireMockIp) {
        this.infra = infra;
        this.wireMockIp = wireMockIp;
    }

    @SneakyThrows
    public void start() {
        buildImage();
        containerId = createAndStartContainer();
        waitUntilHealthy();
    }

    public String baseUrl() {
        return "http://" + infra.getDinDHost() + ":" + infra.getDinDMappedPort9753();
    }

    public String getContainerId() {
        return containerId;
    }

    @SneakyThrows
    private void buildImage() {
        Path projectRoot = Path.of(System.getProperty("user.dir")).getParent();

        Path dockerfile = projectRoot.resolve("docker/Dockerfile");
        if (!Files.exists(dockerfile)) {
            throw new IllegalStateException("Dockerfile not found at: " + dockerfile);
        }

        Path buildContext = Files.createTempDirectory("porthole-it-build-context");

        // Copy the native binary to buildContext/porthole
        Files.copy(
                projectRoot.resolve("server/target/porthole"),
                buildContext.resolve("porthole"),
                StandardCopyOption.REPLACE_EXISTING);

        // Create buildContext/docker/ directory
        Path buildContextDocker = buildContext.resolve("docker");
        Files.createDirectories(buildContextDocker);

        // Copy docker/entrypoint.sh → buildContext/docker/entrypoint.sh
        Files.copy(
                projectRoot.resolve("docker/entrypoint.sh"),
                buildContextDocker.resolve("entrypoint.sh"),
                StandardCopyOption.REPLACE_EXISTING);

        // Copy docker/templates/ → buildContext/docker/templates/ (recursively)
        copyDirectory(projectRoot.resolve("docker/templates"), buildContextDocker.resolve("templates"));

        // Copy Dockerfile into build context root
        Files.copy(dockerfile, buildContext.resolve("Dockerfile"), StandardCopyOption.REPLACE_EXISTING);

        infra.getDinDClient()
                .buildImageCmd(buildContext.toFile())
                .withTags(Set.of(IMAGE_TAG))
                .start()
                .awaitImageId();
    }

    private String createAndStartContainer() {
        DockerClient dindClient = infra.getDinDClient();

        String id = dindClient
                .createContainerCmd(IMAGE_TAG)
                .withName("porthole-it")
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(
                                new PortBinding(Ports.Binding.bindPort(PORTHOLE_PORT), ExposedPort.tcp(PORTHOLE_PORT)))
                        .withBinds(new Bind("/var/run/docker.sock", new Volume("/var/run/docker.sock")))
                        .withExtraHosts("wiremock:" + wireMockIp))
                .withExposedPorts(ExposedPort.tcp(PORTHOLE_PORT))
                .withEnv(
                        "PORTHOLE_DOCKER_HOST=unix:///var/run/docker.sock",
                        "REGISTRY_URLS_REGISTRY=http://wiremock:8080/v2/",
                        "REGISTRY_URLS_AUTH=http://wiremock:8080/auth?service=registry.docker.io&scope=repository:",
                        "REGISTRY_URLS_REPOSITORIES=http://wiremock:8080/v2/repositories/",
                        "REGISTRY_CACHE_TTL=1ms",
                        "REGISTRY_CACHE_VERSION_MAX_SIZE=1")
                .exec()
                .getId();

        dindClient.startContainerCmd(id).exec();
        return id;
    }

    @SneakyThrows
    private void waitUntilHealthy() {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        String healthUrl = baseUrl() + "/actuator/health";
        long deadline = System.currentTimeMillis() + 120_000;

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(healthUrl))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 500) {
                    return;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Porthole did not become healthy within 120s at " + healthUrl);
    }

    @SneakyThrows
    private static void copyDirectory(Path source, Path target) {
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Files.copy(src, target.resolve(source.relativize(src)), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
