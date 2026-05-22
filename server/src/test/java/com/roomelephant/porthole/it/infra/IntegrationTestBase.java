package com.roomelephant.porthole.it.infra;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public abstract class IntegrationTestBase {

    private static final WireMockServer wireMockServer;
    protected static final PortholeContainer porthole;

    private final RestTemplate restTemplate = createRestTemplate();

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        porthole = new PortholeContainer(wireMockServer.port());
        porthole.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            porthole.stop();
            wireMockServer.stop();
        }));
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

    protected static @NotNull WireMockServer wireMockClient() {
        return wireMockServer;
    }

    protected @NotNull ResponseEntity<String> fetch(String url) {
        return fetch(url, String.class);
    }

    protected <T> @NotNull ResponseEntity<T> fetch(String url, Class<T> responseType) {
        return restTemplate.getForEntity(porthole.baseUrl() + url, responseType);
    }

    private static @NotNull RestTemplate createRestTemplate() {
        var template = new RestTemplate();
        template.setErrorHandler(response -> response.getStatusCode().is5xxServerError());
        return template;
    }
}
