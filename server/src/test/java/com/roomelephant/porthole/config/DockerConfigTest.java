package com.roomelephant.porthole.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.roomelephant.porthole.config.properties.*;
import com.roomelephant.porthole.outbound.docker.DockerSourceRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DockerConfigTest {
    private DockerConnectionProperties connection(String id) {
        return new DockerConnectionProperties(
                id, id, "unix:///" + id + ".sock", null, Duration.ofSeconds(5), Duration.ofSeconds(60));
    }

    @Test
    void registryRejectsEmptyDuplicateAndUnknownSources() {
        List<com.roomelephant.porthole.outbound.docker.DockerConnection> emptySources = List.of();
        assertThatThrownBy(() -> new DockerSourceRegistry(emptySources)).isInstanceOf(IllegalArgumentException.class);
        var client = mock(DockerClient.class);
        var source = new com.roomelephant.porthole.outbound.docker.DockerConnection(
                "a", "A", null, Duration.ofSeconds(5), Duration.ofSeconds(60), client);
        var duplicateSources = List.of(source, source);
        assertThatThrownBy(() -> new DockerSourceRegistry(duplicateSources))
                .isInstanceOf(IllegalArgumentException.class);
        var registry = new DockerSourceRegistry(List.of(source));
        assertThatThrownBy(() -> registry.require("missing")).hasMessageContaining("missing");
    }

    @Test
    void closingRetainsSuppressedFailuresFromAllSources() throws Exception {
        var a = mock(DockerClient.class);
        var b = mock(DockerClient.class);
        doThrow(new java.io.IOException("first")).when(a).close();
        doThrow(new java.io.IOException("second")).when(b).close();
        var sources = new DockerSourceRegistry(List.of(
                new com.roomelephant.porthole.outbound.docker.DockerConnection(
                        "a", "A", null, Duration.ofSeconds(5), Duration.ofSeconds(60), a),
                new com.roomelephant.porthole.outbound.docker.DockerConnection(
                        "b", "B", null, Duration.ofSeconds(5), Duration.ofSeconds(60), b)));
        assertThatThrownBy(sources::close)
                .hasMessage("first")
                .satisfies(e -> assertThat(e.getSuppressed())
                        .extracting(Throwable::getMessage)
                        .containsExactly("second"));
    }

    @Test
    void doesNotPingDuringStartupAndClosesEveryClient() throws Exception {
        var a = mock(DockerClient.class);
        var b = mock(DockerClient.class);
        var config = new DockerConfig(new DockerProperties(List.of(connection("a"), connection("b")))) {
            @Override
            DockerClient createClient(DockerConnectionProperties p) {
                return p.id().equals("a") ? a : b;
            }
        };
        try (var sources = config.dockerSources()) {
            assertThat(sources.sources()).extracting(s -> s.id()).containsExactly("a", "b");
            assertThat(sources.require("b").client()).isSameAs(b);
            verifyNoInteractions(a, b);
        }
        verify(a).close();
        verify(b).close();
    }

    @Test
    void cleansUpPartialConstruction() throws Exception {
        var client = mock(DockerClient.class);
        var config = new DockerConfig(new DockerProperties(List.of(connection("a"), connection("b")))) {
            @Override
            DockerClient createClient(DockerConnectionProperties p) {
                if (p.id().equals("b")) throw new IllegalStateException("failed");
                return client;
            }
        };
        assertThatThrownBy(config::dockerSources).hasMessage("failed");
        verify(client).close();
    }

    @Test
    void closesOtherClientsWhenOneCloseFails() throws Exception {
        var a = mock(DockerClient.class);
        var b = mock(DockerClient.class);
        doThrow(new java.io.IOException("close failed")).when(a).close();
        var sources = new DockerSourceRegistry(List.of(
                new com.roomelephant.porthole.outbound.docker.DockerConnection(
                        "a", "A", null, Duration.ofSeconds(5), Duration.ofSeconds(60), a),
                new com.roomelephant.porthole.outbound.docker.DockerConnection(
                        "b", "B", null, Duration.ofSeconds(5), Duration.ofSeconds(60), b)));
        assertThatThrownBy(sources::close).hasMessage("close failed");
        verify(b).close();
    }
}
