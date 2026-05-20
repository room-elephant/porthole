package com.roomelephant.porthole.it.infra;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.http.ResponseEntity;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public abstract class IntegrationTestBase {

    protected static final String TEST_LOCAL_IMAGE_TAG = "my-local-image:1.0";
    protected static final String TEST_APP_CONTAINER_NAME = "porthole-test-app";
    protected static final String TEST_NO_PORTS_CONTAINER_NAME = "porthole-test-no-ports";
    protected static final String TEST_STOPPED_CONTAINER_NAME = "porthole-test-stopped";
    protected static final String TEST_LOCAL_CONTAINER_NAME = "porthole-test-local";
    protected static final String BUSYBOX_IMAGE = "busybox:1.37.0-uclibc";
    protected static final String BUSYBOX_LATEST_IMAGE = "busybox:latest";

    private static final String[] CONTAINER_CMD = {"sh", "-c", "while true; do sleep 3600; done"};

    protected static GenericContainer<?> testAppContainer;
    protected static GenericContainer<?> localContainer;
    protected static GenericContainer<?> noPortsContainer;

    private static final WireMockServer wireMockServer;
    protected static final PortholeContainer porthole;
    private static final WireMock wireMockClient;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        porthole = new PortholeContainer(wireMockServer.port());
        porthole.start();

        wireMockClient = new WireMock("localhost", wireMockServer.port());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            porthole.stop();
            wireMockServer.stop();
        }));
    }

    private final org.springframework.web.client.RestTemplate restTemplate = createRestTemplate();

    private static org.springframework.web.client.RestTemplate createRestTemplate() {
        var template = new org.springframework.web.client.RestTemplate();
        template.setErrorHandler(response -> response.getStatusCode().is5xxServerError());
        return template;
    }

    protected static WireMock wireMockClient() {
        return wireMockClient;
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        if (testInfo.getTestMethod().isPresent()
                && testInfo.getTestMethod().get().isAnnotationPresent(RunWithoutContainers.class)) {
            cleanupDocker();
            return;
        }
        if (areContainersRunning()) {
            return;
        }
        createContainers();
    }

    @AfterEach
    protected void tearDown() {
        List<ServeEvent> allServeEvents = wireMockServer.getAllServeEvents();

        List<ServeEvent> unmatchedEvents =
                allServeEvents.stream().filter(e -> !e.getWasMatched()).toList();

        if (!unmatchedEvents.isEmpty()) {
            String details = unmatchedEvents.stream()
                    .map(e -> e.getRequest().getMethod() + " " + e.getRequest().getUrl())
                    .toList()
                    .toString();
            wireMockServer.resetAll();
            throw new AssertionError("The following requests were made but not matched by any stub: " + details);
        }

        List<StubMapping> allStubs = wireMockServer.listAllStubMappings().getMappings();
        List<StubMapping> unusedStubs = allStubs.stream()
                .filter(stub -> allServeEvents.stream()
                        .noneMatch(event -> event.getStubMapping() != null
                                && event.getStubMapping().getId().equals(stub.getId())))
                .toList();

        if (!unusedStubs.isEmpty()) {
            wireMockServer.resetAll();
            throw new AssertionError("The following stubs were defined but never matched: " + unusedStubs);
        }

        wireMockServer.resetAll();
    }

    protected @NotNull ResponseEntity<String> fetch(String url) {
        return fetch(url, String.class);
    }

    protected <T> @NotNull ResponseEntity<T> fetch(String url, Class<T> responseType) {
        return restTemplate.getForEntity(porthole.baseUrl() + url, responseType);
    }

    private boolean areContainersRunning() {
        return testAppContainer != null
                && testAppContainer.isRunning()
                && noPortsContainer != null
                && noPortsContainer.isRunning()
                && localContainer != null
                && localContainer.isRunning();
    }

    private void cleanupDocker() {
        if (testAppContainer != null) {
            testAppContainer.stop();
            testAppContainer = null;
        }
        if (localContainer != null) {
            localContainer.stop();
            localContainer = null;
        }
        if (noPortsContainer != null) {
            noPortsContainer.stop();
            noPortsContainer = null;
        }
        try {
            DockerClientFactory.instance()
                    .client()
                    .removeContainerCmd(TEST_STOPPED_CONTAINER_NAME)
                    .withForce(true)
                    .exec();
        } catch (Exception ignored) {
        }
    }

    private void createContainers() {
        var dc = DockerClientFactory.instance().client();

        // Ensure source images are available locally before using them
        try {
            dc.pullImageCmd(BUSYBOX_IMAGE).start().awaitCompletion();
            dc.pullImageCmd(BUSYBOX_LATEST_IMAGE).start().awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while pulling images", e);
        }

        // Tag a local image (simulates an image not from a public registry)
        dc.tagImageCmd(BUSYBOX_IMAGE, "my-local-image", "1.0").exec();

        // App Container (running, has public port)
        testAppContainer = new GenericContainer<>(BUSYBOX_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_APP_CONTAINER_NAME))
                .withCommand(CONTAINER_CMD)
                .withExposedPorts(8080)
                .waitingFor(Wait.forSuccessfulCommand("true"));
        testAppContainer.start();

        // Stopped Container (created but never started — uses raw docker-java because
        // GenericContainer.stop() also removes the container)
        try {
            dc.removeContainerCmd(TEST_STOPPED_CONTAINER_NAME).withForce(true).exec();
        } catch (Exception ignored) {
        }
        dc.createContainerCmd(BUSYBOX_IMAGE)
                .withName(TEST_STOPPED_CONTAINER_NAME)
                .withExposedPorts(ExposedPort.tcp(8081))
                .exec();

        // Local Image Container (running, has public port, uses the locally tagged image)
        localContainer = new GenericContainer<>(DockerImageName.parse(TEST_LOCAL_IMAGE_TAG))
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_LOCAL_CONTAINER_NAME))
                .withCommand(CONTAINER_CMD)
                .withExposedPorts(8082)
                .withImagePullPolicy(imageName -> false)
                .waitingFor(Wait.forSuccessfulCommand("true"));
        localContainer.start();

        // No-Ports Container (running, no exposed ports)
        noPortsContainer = new GenericContainer<>(BUSYBOX_LATEST_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_NO_PORTS_CONTAINER_NAME))
                .withCommand(CONTAINER_CMD);
        noPortsContainer.start();
    }
}
