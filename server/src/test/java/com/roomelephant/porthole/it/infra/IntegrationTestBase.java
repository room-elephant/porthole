package com.roomelephant.porthole.it.infra;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;

public abstract class IntegrationTestBase {

    protected static final String TEST_LOCAL_IMAGE_TAG = "my-local-image:1.0";
    protected static final String TEST_APP_CONTAINER_NAME = "porthole-test-app";
    protected static final String TEST_NO_PORTS_CONTAINER_NAME = "porthole-test-no-ports";
    protected static final String TEST_STOPPED_CONTAINER_NAME = "porthole-test-stopped";
    protected static final String TEST_LOCAL_CONTAINER_NAME = "porthole-test-local";
    protected static final String BUSYBOX_IMAGE = "busybox:1.37.0-uclibc";
    protected static final String BUSYBOX_LATEST_IMAGE = "busybox:latest";

    protected static GenericContainer<?> testAppContainer;
    protected static GenericContainer<?> localContainer;
    protected static GenericContainer<?> noPortsContainer;

    protected static final DockerInfrastructure dockerInfra;
    protected static final GenericContainer<?> wireMockContainer;
    protected static final PortholeContainer porthole;
    private static final WireMock wireMockClient;

    static {
        dockerInfra = new DockerInfrastructure();
        wireMockContainer = dockerInfra.startWireMock();
        String wireMockIp = wireMockContainer.getContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .values()
                .iterator()
                .next()
                .getIpAddress();
        porthole = dockerInfra.startPorthole(wireMockIp);
        wireMockClient = new WireMock("localhost", wireMockContainer.getMappedPort(8080));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            wireMockContainer.stop();
            dockerInfra.close();
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
        List<ServeEvent> allServeEvents = wireMockClient().getServeEvents();

        List<ServeEvent> unmatchedEvents =
                allServeEvents.stream().filter(e -> !e.getWasMatched()).toList();

        if (!unmatchedEvents.isEmpty()) {
            String details = unmatchedEvents.stream()
                    .map(e -> e.getRequest().getMethod() + " " + e.getRequest().getUrl())
                    .toList()
                    .toString();
            wireMockClient().resetToDefaultMappings();
            throw new AssertionError("The following requests were made but not matched by any stub: " + details);
        }

        List<StubMapping> allStubs =
                wireMockClient().listAllStubMappings().getMappings();
        List<StubMapping> unusedStubs = allStubs.stream()
                .filter(stub -> allServeEvents.stream()
                        .noneMatch(event -> event.getStubMapping() != null
                                && event.getStubMapping().getId().equals(stub.getId())))
                .toList();

        if (!unusedStubs.isEmpty()) {
            wireMockClient().resetToDefaultMappings();
            throw new AssertionError("The following stubs were defined but never matched: " + unusedStubs);
        }

        wireMockClient().resetToDefaultMappings();
    }

    protected void pauseDocker() {
        dockerInfra.pauseDocker();
    }

    protected void unpauseDocker() {
        dockerInfra.unpauseDocker();
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
        testAppContainer = null;
        dockerInfra.removeContainerQuietly(TEST_APP_CONTAINER_NAME);

        noPortsContainer = null;
        dockerInfra.removeContainerQuietly(TEST_NO_PORTS_CONTAINER_NAME);

        localContainer = null;
        dockerInfra.removeContainerQuietly(TEST_LOCAL_CONTAINER_NAME);

        dockerInfra.removeContainerQuietly(TEST_STOPPED_CONTAINER_NAME);
    }

    private void createContainers() {
        dockerInfra.pullImage(BUSYBOX_IMAGE);
        dockerInfra.pullImage(BUSYBOX_LATEST_IMAGE);

        // App Container (Running with ports)
        dockerInfra.removeContainerQuietly(TEST_APP_CONTAINER_NAME);
        testAppContainer = dockerInfra.buildImage(BUSYBOX_IMAGE, 8080, TEST_APP_CONTAINER_NAME);
        testAppContainer.start();

        // Stopped Container
        dockerInfra.removeContainerQuietly(TEST_STOPPED_CONTAINER_NAME);
        dockerInfra.buildStoppedImage(BUSYBOX_IMAGE, 8081, TEST_STOPPED_CONTAINER_NAME);

        // Local Image Container
        dockerInfra.removeContainerQuietly(TEST_LOCAL_CONTAINER_NAME);
        localContainer =
                dockerInfra.buildLocalImage(BUSYBOX_IMAGE, 8082, TEST_LOCAL_CONTAINER_NAME, TEST_LOCAL_IMAGE_TAG);
        localContainer.start();

        // No Ports Container
        dockerInfra.removeContainerQuietly(TEST_NO_PORTS_CONTAINER_NAME);
        noPortsContainer = dockerInfra.buildImage(BUSYBOX_LATEST_IMAGE, null, TEST_NO_PORTS_CONTAINER_NAME);
        noPortsContainer.start();
    }
}
