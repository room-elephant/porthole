package com.roomelephant.porthole.core.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.*;
import com.github.dockerjava.api.command.*;
import com.roomelephant.porthole.core.enrichment.*;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import com.roomelephant.porthole.core.presentation.*;
import com.roomelephant.porthole.core.presentation.icon.*;
import com.roomelephant.porthole.outbound.docker.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MultipleSourcesTest {
    private final List<DockerDiscoveryService> discoveries = new ArrayList<>();

    @AfterEach
    void closeDiscoveries() {
        discoveries.forEach(DockerDiscoveryService::close);
    }

    private DockerClient client() {
        var client = mock(DockerClient.class);
        var list = mock(ListContainersCmd.class);
        when(client.listContainersCmd()).thenReturn(list);
        when(list.withShowAll(true)).thenReturn(list);
        var container = mock(com.github.dockerjava.api.model.Container.class);
        when(container.getId()).thenReturn("same-id");
        when(container.getNames()).thenReturn(new String[] {"/same-name"});
        when(container.getImage()).thenReturn("nginx:1");
        when(list.exec()).thenReturn(List.of(container));
        return client;
    }

    private DockerConnection source(String id, DockerClient client, Duration timeout) {
        return new DockerConnection(
                id, id, "192.168.50." + (id.equals("walker") ? "6" : "5"), timeout, timeout, true, client);
    }

    private ContainerService service(DockerOperations operations, ContainerEnrichment enrichment) {
        var discovery = new DockerDiscoveryService(operations);
        discoveries.add(discovery);
        return new ContainerService(
                discovery,
                new ContainerPresentation(mock(IconResolver.class, i -> "icon")),
                enrichment,
                SelfContainer.none());
    }

    @Test
    void closedDiscoveryRejectsInventoryRequests() {
        var service = service(
                new DockerJavaOperations(
                        new DockerSourceRegistry(List.of(source("walker", client(), Duration.ofSeconds(1))))),
                new ContainerEnrichment(List.of()));
        discoveries.getFirst().close();
        var query = new ContainerQuery(true, true);
        assertThatThrownBy(() -> service.getContainers(query)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void oneRequestFetchesEverySourceAndKeepsMatchingIdsSeparate() {
        var registry = new DockerJavaOperations(new DockerSourceRegistry(List.of(
                source("walker", client(), Duration.ofSeconds(1)), source("mike", client(), Duration.ofSeconds(1)))));
        var service = service(registry, new ContainerEnrichment(List.of()));
        var result = service.getContainers(new ContainerQuery(true, true));
        assertThat(result.containers()).extracting(Container::sourceId).containsExactly("walker", "mike");
        assertThat(result.containers()).extracting(Container::id).containsExactly("same-id", "same-id");
        assertThat(result.sources()).extracting(SourceState::status).containsExactly(SourceStatus.UP, SourceStatus.UP);
    }

    @Test
    void slowOrFailedSourceCannotDiscardHealthyResults() {
        var a = client();
        var b = client();
        var release = new CountDownLatch(1);
        when(b.listContainersCmd().withShowAll(true).exec()).thenAnswer(i -> {
            release.await();
            return List.of();
        });
        var registry = new DockerJavaOperations(new DockerSourceRegistry(
                List.of(source("mike", b, Duration.ofMillis(50)), source("walker", a, Duration.ofSeconds(1)))));
        var service = service(registry, new ContainerEnrichment(List.of()));
        try {
            var result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                    Duration.ofSeconds(2), () -> service.getContainers(new ContainerQuery(true, true)));
            assertThat(result.containers()).extracting(Container::sourceId).containsExactly("walker");
            assertThat(result.sources())
                    .extracting(SourceState::status)
                    .containsExactly(SourceStatus.DOWN, SourceStatus.UP);
            assertThat(result.sources().getFirst().error()).doesNotContain("sock");
        } finally {
            release.countDown();
        }
    }

    @Test
    void dockerDeadlineDoesNotCancelEnrichment() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ContainerEnricher slowEnricher = (container, context) -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Enrichment was not released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return Optional.of(container);
        };
        var service = service(
                new DockerJavaOperations(
                        new DockerSourceRegistry(List.of(source("walker", client(), Duration.ofMillis(100))))),
                new ContainerEnrichment(List.of(slowEnricher)));
        try (var caller = Executors.newVirtualThreadPerTaskExecutor()) {
            var response = caller.submit(() -> service.getContainers(new ContainerQuery(true, true)));
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> response.get(500, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                release.countDown();
                var result = response.get(2, TimeUnit.SECONDS);
                assertThat(result.sources()).extracting(SourceState::status).containsExactly(SourceStatus.UP);
                assertThat(result.containers()).extracting(Container::sourceId).containsExactly("walker");
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void allSourceFailuresAreReportedInsteadOfAnEmptySuccess() {
        var client = client();
        when(client.listContainersCmd().withShowAll(true).exec())
                .thenThrow(new RuntimeException(new java.net.SocketException("secret socket path")));
        var service = service(
                new DockerJavaOperations(
                        new DockerSourceRegistry(List.of(source("walker", client, Duration.ofSeconds(1))))),
                new ContainerEnrichment(List.of()));
        var result = service.getContainers(new ContainerQuery(true, true));
        assertThat(result.containers()).isEmpty();
        assertThat(result.sources().getFirst().status()).isEqualTo(SourceStatus.DOWN);
        assertThat(result.sources().getFirst().error()).isEqualTo("Docker is not reachable");
    }

    @Test
    void enrichmentFailureDoesNotDiscardOtherSourcesOrReportDockerAsDown() {
        var registry = new DockerJavaOperations(new DockerSourceRegistry(List.of(
                source("walker", client(), Duration.ofSeconds(1)), source("mike", client(), Duration.ofSeconds(1)))));
        ContainerEnricher failingEnricher = (container, context) -> {
            if (container.sourceId().equals("walker"))
                throw new com.roomelephant.porthole.core.model.exception.DockerUnavailableException(
                        new java.net.SocketException("private details"));
            return Optional.of(container);
        };
        var service = service(registry, new ContainerEnrichment(List.of(failingEnricher)));
        var result = service.getContainers(new ContainerQuery(true, true));
        assertThat(result.sources())
                .extracting(SourceState::status)
                .containsExactly(SourceStatus.ERROR, SourceStatus.UP);
        assertThat(result.sources().getFirst().error()).doesNotContain("private");
        assertThat(result.containers()).extracting(Container::sourceId).containsExactly("mike");
    }
}
