package com.roomelephant.porthole.it.infra;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.roomelephant.porthole.controller.model.ContainerDTO;
import com.roomelephant.porthole.domain.model.VersionInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/** Each test owns its app and registry so background checks cannot cross stub lifetimes. */
public abstract class IntegrationTestBase {
    private WireMockServer wireMockServer;
    protected PortholeContainer porthole;
    private final Set<UUID> inventoryDefaults = new HashSet<>();
    private final RestTemplate restTemplate = createRestTemplate();

    @BeforeEach
    protected void setUpApplication() {
        wireMockServer = new WireMockServer(registryConfiguration());
        wireMockServer.start();
        // Docker inventory includes unrelated host containers. Explicit low-priority defaults
        // model unavailable metadata for those repositories; test-specific stubs override them.
        inventoryDefaults.add(wireMockServer
                .stubFor(get(urlPathMatching("/(auth|ghcr/token)"))
                        .atPriority(100)
                        .willReturn(okJson("{\"token\":\"inventory-token\"}")))
                .getId());
        inventoryDefaults.add(wireMockServer
                .stubFor(get(urlPathMatching("/(v2/repositories/.+/tags|ghcr/v2/.+/tags/list)"))
                        .atPriority(100)
                        .willReturn(aResponse().withStatus(404)))
                .getId());
        inventoryDefaults.add(wireMockServer
                .stubFor(head(urlPathMatching("/(v2|ghcr/v2)/.+/manifests/.+"))
                        .atPriority(100)
                        .willReturn(aResponse().withStatus(404)))
                .getId());
        porthole = new PortholeContainer(wireMockServer.port());
    }

    protected WireMockConfiguration registryConfiguration() {
        return WireMockConfiguration.options().dynamicPort();
    }

    protected void startPorthole() {
        if (!porthole.isRunning()) porthole.start();
    }

    protected void stopPorthole() {
        if (porthole == null || !porthole.isRunning()) return;
        try {
            Files.writeString(Path.of("target/porthole-" + getClass().getSimpleName() + ".log"), porthole.getLogs());
        } catch (Exception ignored) {
        }
        porthole.stop();
    }

    @AfterEach
    protected void tearDown() {
        try {
            stopPorthole();
            List<ServeEvent> events = wireMockServer.getAllServeEvents();
            List<String> unmatched = events.stream()
                    .filter(e -> !e.getWasMatched())
                    .map(e -> e.getRequest().getMethod() + " " + e.getRequest().getUrl())
                    .toList();
            assertThat(unmatched).as("Unmatched registry requests").isEmpty();
            List<StubMapping> unused = wireMockServer.listAllStubMappings().getMappings().stream()
                    .filter(stub -> !inventoryDefaults.contains(stub.getId()))
                    .filter(stub -> events.stream()
                            .noneMatch(event -> event.getStubMapping() != null
                                    && event.getStubMapping().getId().equals(stub.getId())))
                    .toList();
            assertThat(unused).as("Unused test-specific registry stubs").isEmpty();
        } finally {
            wireMockServer.stop();
        }
    }

    protected @NotNull WireMockServer wireMockClient() {
        return wireMockServer;
    }

    protected @NotNull ResponseEntity<String> fetch(String url) {
        return fetch(url, String.class);
    }

    protected <T> @NotNull ResponseEntity<T> fetch(String url, Class<T> responseType) {
        startPorthole();
        return restTemplate.getForEntity(porthole.baseUrl() + url, responseType);
    }

    protected VersionInfo fetchCachedVersion(String containerId) {
        // Container startup has its own readiness timeout; this budget is for resolution only.
        startPorthole();
        var result = new AtomicReference<VersionInfo>();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ContainerDTO[] containers = fetch(
                            "/api/containers?includeWithoutEndpoints=true&includeStopped=true", ContainerDTO[].class)
                    .getBody();
            assertThat(containers).isNotNull();
            VersionInfo version = Arrays.stream(containers)
                    .filter(c -> c.id().equals(containerId))
                    .findFirst()
                    .orElseThrow()
                    .version();
            assertThat(version).isNotNull();
            result.set(version);
        });
        return result.get();
    }

    private static RestTemplate createRestTemplate() {
        var template = new RestTemplate();
        template.setErrorHandler(response -> response.getStatusCode().is5xxServerError());
        return template;
    }
}
