package com.roomelephant.porthole.domain.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.*;
import com.github.dockerjava.api.command.*;
import com.roomelephant.porthole.domain.component.*;
import com.roomelephant.porthole.domain.mapper.*;
import com.roomelephant.porthole.domain.model.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class MultipleSourcesTest {
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

    private DockerSource source(String id, DockerClient client, Duration timeout) {
        return new DockerSource(id, id, "192.168.50." + (id.equals("walker") ? "6" : "5"), timeout, timeout, client);
    }

    @Test
    void closedInventoryRejectsRequests() {
        var service = new ContainerService(
                new DockerSourceRegistry(List.of(source("walker", client(), Duration.ofSeconds(1)))),
                new ContainerMapper(mock(IconComponent.class)),
                new ContainerEnrichment(List.of()));
        service.close();
        assertThatThrownBy(() -> service.getContainers(true, true, "proxy")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void oneRequestFetchesEverySourceAndKeepsMatchingIdsSeparate() {
        var registry = new DockerSourceRegistry(List.of(
                source("walker", client(), Duration.ofSeconds(1)), source("mike", client(), Duration.ofSeconds(1))));
        try (var service = new ContainerService(
                registry,
                new ContainerMapper(mock(IconComponent.class, i -> "icon")),
                new ContainerEnrichment(List.of()))) {
            var result = service.getContainers(true, true, "proxy");
            assertThat(result.containers()).extracting(Container::sourceId).containsExactly("walker", "mike");
            assertThat(result.containers()).extracting(Container::id).containsExactly("same-id", "same-id");
            assertThat(result.sources())
                    .extracting(SourceState::status)
                    .containsExactly(SourceStatus.UP, SourceStatus.UP);
        }
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
        var registry = new DockerSourceRegistry(
                List.of(source("mike", b, Duration.ofMillis(50)), source("walker", a, Duration.ofSeconds(1))));
        try (var service = new ContainerService(
                registry,
                new ContainerMapper(mock(IconComponent.class, i -> "icon")),
                new ContainerEnrichment(List.of()))) {
            var result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                    Duration.ofSeconds(2), () -> service.getContainers(true, true, "proxy"));
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
    void allSourceFailuresAreReportedInsteadOfAnEmptySuccess() {
        var client = client();
        when(client.listContainersCmd().withShowAll(true).exec())
                .thenThrow(new RuntimeException(new java.net.SocketException("secret socket path")));
        try (var service = new ContainerService(
                new DockerSourceRegistry(List.of(source("walker", client, Duration.ofSeconds(1)))),
                new ContainerMapper(mock(IconComponent.class)),
                new ContainerEnrichment(List.of()))) {
            var result = service.getContainers(true, true, "proxy");
            assertThat(result.containers()).isEmpty();
            assertThat(result.sources().getFirst().status()).isEqualTo(SourceStatus.DOWN);
            assertThat(result.sources().getFirst().error()).isEqualTo("Docker is not reachable");
        }
    }
}
