package com.roomelephant.porthole.it.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.roomelephant.porthole.inbound.web.dto.ContainerDTO;
import com.roomelephant.porthole.it.infra.IntegrationTestBase;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class AsyncVersionRefreshIT extends IntegrationTestBase {
    private static final String TAGS = "/v2/repositories/library/busybox/tags?page_size=100";
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch completed = new CountDownLatch(1);
    private volatile boolean block;
    private GenericContainer<?> fixture;

    @Override
    protected WireMockConfiguration registryConfiguration() {
        return super.registryConfiguration().extensions(new ServeEventListener() {
            @Override
            public String getName() {
                return "version-check-gate";
            }

            @Override
            public void afterMatch(ServeEvent event, Parameters parameters) {
                if (!event.getRequest().getUrl().equals(TAGS)) return;
                entered.countDown();
                if (block) {
                    try {
                        if (!release.await(30, TimeUnit.SECONDS))
                            throw new IllegalStateException("Test did not release registry response");
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            @Override
            public void afterComplete(ServeEvent event, Parameters parameters) {
                if (event.getRequest().getUrl().equals(TAGS)) completed.countDown();
            }
        });
    }

    @BeforeEach
    void createFixture() {
        fixture = new GenericContainer<>("docker.io/library/busybox:1.36.1")
                .withCommand("sh", "-c", "echo ready; sleep 3600")
                .waitingFor(Wait.forLogMessage(".*ready.*", 1));
        fixture.start();
        wireMockClient().stubFor(get(urlEqualTo(TAGS)).willReturn(okJson("{\"results\":[{\"name\":\"2.0\"}]}")));
    }

    @AfterEach
    void stopFixture() {
        release.countDown();
        stopPorthole();
        fixture.stop();
    }

    @Test
    void bootChecksVersionsBeforeAnyContainerRequest() throws Exception {
        startPorthole();
        assertThat(completed.await(20, TimeUnit.SECONDS)).isTrue();
        assertThat(fetchCachedVersion(fixture.getContainerId()).latestVersion()).isEqualTo("2.0");
    }

    @Test
    void listingReturnsWhileRegistryCheckIsBlocked() throws Exception {
        block = true;
        porthole.withEnv("PORTHOLE_REGISTRY_TIMEOUT_READ", "30s");
        startPorthole();
        try {
            assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
            var response = new org.springframework.web.client.RestTemplate()
                    .postForEntity(porthole.baseUrl() + "/api/versions/refresh", null, String.class);
            assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.ACCEPTED);
            ContainerDTO container = listedFixture();
            assertThat(container.version()).isNull();
            assertThat(completed.getCount()).isEqualTo(1);
        } finally {
            release.countDown();
        }
        assertThat(fetchCachedVersion(fixture.getContainerId()).updateAvailable())
                .isTrue();
    }

    @Test
    void manualRefreshBypassesIntervalAndRegistryCache() {
        porthole.withEnv("PORTHOLE_VERSIONS_REFRESHINTERVAL", "1h")
                .withEnv("PORTHOLE_REGISTRY_CACHE_TTL", "1h")
                .withEnv("PORTHOLE_REGISTRY_CACHE_VERSIONMAXSIZE", "1000");
        assertThat(fetchCachedVersion(fixture.getContainerId()).latestVersion()).isEqualTo("2.0");
        wireMockClient()
                .stubFor(get(urlEqualTo(TAGS)).atPriority(1).willReturn(okJson("{\"results\":[{\"name\":\"3.0\"}]}")));
        var response = new org.springframework.web.client.RestTemplate()
                .postForEntity(porthole.baseUrl() + "/api/versions/refresh", null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.ACCEPTED);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(listedFixture().version().latestVersion()).isEqualTo("3.0"));
    }

    @Test
    void configuredScheduleRefreshesAndKeepsSuccessDuringRegistryOutage() {
        porthole.withEnv("PORTHOLE_VERSIONS_REFRESHINTERVAL", "250ms");
        assertThat(fetchCachedVersion(fixture.getContainerId()).latestVersion()).isEqualTo("2.0");
        var newer = wireMockClient()
                .stubFor(get(urlEqualTo(TAGS)).atPriority(1).willReturn(okJson("{\"results\":[{\"name\":\"3.0\"}]}")));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(listedFixture().version().latestVersion()).isEqualTo("3.0"));
        wireMockClient().removeStub(newer);
        var failed =
                wireMockClient().stubFor(get(urlEqualTo(TAGS)).atPriority(1).willReturn(serverError()));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(wireMockClient().getAllServeEvents().stream()
                                .anyMatch(e -> e.getStubMapping() != null
                                        && e.getStubMapping().getId().equals(failed.getId())))
                        .isTrue());
        assertThat(listedFixture().version().latestVersion()).isEqualTo("3.0");
        assertThat(listedFixture().version().updateAvailable()).isTrue();
    }

    private ContainerDTO listedFixture() {
        return Arrays.stream(fetch("/api/containers?includeWithoutEndpoints=true", ContainerDTO[].class)
                        .getBody())
                .filter(c -> c.id().equals(fixture.getContainerId()))
                .findFirst()
                .orElseThrow();
    }
}
