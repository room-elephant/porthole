package com.roomelephant.porthole.it.infra;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.ExposedPort;
import java.util.Map;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class ContainerAwareIntegrationTestBase extends IntegrationTestBase {

    protected static final String TEST_LOCAL_IMAGE_TAG = "my-local-image:1.0";
    protected static final String TEST_APP_CONTAINER_NAME = "porthole-test-app";
    protected static final String TEST_NO_PORTS_CONTAINER_NAME = "porthole-test-no-ports";
    protected static final String TEST_STOPPED_CONTAINER_NAME = "porthole-test-stopped";
    protected static final String TEST_LOCAL_CONTAINER_NAME = "porthole-test-local";
    protected static final String PAUSE_IMAGE = "registry.k8s.io/pause:3.9";
    protected static final String PAUSE_LATEST_IMAGE = "registry.k8s.io/pause:latest";

    protected static final GenericContainer<?> testAppContainer;
    protected static final GenericContainer<?> localContainer;
    protected static final GenericContainer<?> noPortsContainer;

    protected static final String TEST_PROXY_CONTAINER_NAME = "porthole-test-proxy";
    protected static final String TEST_MIXED_CONTAINER_NAME = "porthole-test-mixed";
    protected static final String TEST_DISABLED_CONTAINER_NAME = "porthole-test-disabled";
    protected static final String TEST_UNSUPPORTED_CONTAINER_NAME = "porthole-test-unsupported";
    protected static final GenericContainer<?> proxyContainer;
    protected static final GenericContainer<?> mixedContainer;
    protected static final GenericContainer<?> disabledContainer;
    protected static final GenericContainer<?> unsupportedContainer;

    static {
        DockerClient dc = DockerClientFactory.instance().client();

        try {
            dc.pullImageCmd(PAUSE_IMAGE).start().awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while pulling images", e);
        }

        dc.tagImageCmd(PAUSE_IMAGE, "my-local-image", "1.0").exec();
        dc.tagImageCmd(PAUSE_IMAGE, "registry.k8s.io/pause", "latest").exec();

        testAppContainer = new GenericContainer<>(PAUSE_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_APP_CONTAINER_NAME))
                .withExposedPorts(8080);
        testAppContainer.start();

        // Uses raw docker-java because GenericContainer.stop() also removes the container
        try {
            dc.removeContainerCmd(TEST_STOPPED_CONTAINER_NAME).withForce(true).exec();
        } catch (Exception ignored) {
        }
        dc.createContainerCmd(PAUSE_IMAGE)
                .withName(TEST_STOPPED_CONTAINER_NAME)
                .withExposedPorts(ExposedPort.tcp(8081))
                .exec();

        localContainer = new GenericContainer<>(DockerImageName.parse(TEST_LOCAL_IMAGE_TAG))
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_LOCAL_CONTAINER_NAME))
                .withExposedPorts(8082)
                .withImagePullPolicy(imageName -> false);
        localContainer.start();

        noPortsContainer = new GenericContainer<>(PAUSE_LATEST_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_NO_PORTS_CONTAINER_NAME))
                .withImagePullPolicy(imageName -> false);
        noPortsContainer.start();

        proxyContainer = endpointFixture(
                TEST_PROXY_CONTAINER_NAME,
                Map.of(
                        "traefik.http.routers.app.rule", "Host(`jellyfin.example.com`)",
                        "traefik.http.routers.app.tls", "true"));
        mixedContainer = endpointFixture(
                TEST_MIXED_CONTAINER_NAME,
                Map.of(
                        "traefik.http.routers.root.rule", "Host(`transmission.example.com`)",
                        "traefik.http.routers.root.tls", "true",
                        "traefik.http.routers.copy.rule", "Host(`TRANSMISSION.example.com`)",
                        "traefik.http.routers.copy.tls.certresolver", "test",
                        "traefik.http.routers.download.rule", "Host(`transmission.example.com`) && PathPrefix(`/dl/`)",
                        "traefik.http.routers.download.tls", "true",
                        "traefik.http.routers.invalid.rule", "Host(`bad.example.com`) && Method(`POST`)",
                        "traefik.http.services.app.loadbalancer.server.port", "9091"),
                51413);
        disabledContainer = endpointFixture(
                TEST_DISABLED_CONTAINER_NAME,
                Map.of(
                        "traefik.enable", "false",
                        "traefik.http.routers.app.rule", "Host(`disabled.example.com`)"),
                8083);
        unsupportedContainer = endpointFixture(
                TEST_UNSUPPORTED_CONTAINER_NAME,
                Map.of("traefik.http.routers.app.rule", "Host(`bad.example.com`) || Host(`other.example.com`)"));
        for (GenericContainer<?> fixture :
                new GenericContainer<?>[] {proxyContainer, mixedContainer, disabledContainer, unsupportedContainer}) {
            fixture.start();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            proxyContainer.stop();
            mixedContainer.stop();
            disabledContainer.stop();
            unsupportedContainer.stop();
        }));
    }

    private static GenericContainer<?> endpointFixture(String name, Map<String, String> labels, Integer... ports) {
        return new GenericContainer<>(PAUSE_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(name))
                .withLabels(labels)
                .withExposedPorts(ports);
    }
}
