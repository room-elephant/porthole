package com.roomelephant.porthole.it.infra;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.SneakyThrows;

public class PortholeContainer {

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
        infra.ensurePortholeImageBuilt();
        containerId = createAndStartContainer();
        waitUntilHealthy();
    }

    public String baseUrl() {
        return "http://" + infra.getDinDHost() + ":" + infra.getDinDMappedPort9753();
    }

    public String getContainerId() {
        return containerId;
    }

    private String createAndStartContainer() {
        DockerClient dindClient = infra.getDinDClient();
        infra.removeContainerQuietly("porthole-it");

        String id = dindClient
                .createContainerCmd(DockerInfrastructure.PORTHOLE_IT_IMAGE_TAG)
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
}
