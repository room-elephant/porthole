package com.roomelephant.porthole.inbound.health;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;
import com.roomelephant.porthole.outbound.docker.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DockerHealthIndicatorTest {
    private DockerConnection source(String id, DockerClient client) {
        return new DockerConnection(id, id, null, Duration.ofSeconds(5), Duration.ofSeconds(60), client);
    }

    @Test
    void oneUpSourceKeepsOverallUpAndDoesNotLeakFailures() {
        var a = mock(DockerClient.class);
        var b = mock(DockerClient.class);
        var ping = mock(PingCmd.class);
        when(a.pingCmd()).thenReturn(ping);
        when(b.pingCmd()).thenThrow(new RuntimeException("secret.sock"));
        try (var health = new DockerHealthIndicator(
                new DockerJavaOperations(new DockerSourceRegistry(List.of(source("a", a), source("b", b)))))) {
            var result = health.health();
            assertThat(result.getStatus()).hasToString("UP");
            assertThat(result.getDetails().toString())
                    .contains("sources", "DOWN")
                    .doesNotContain("secret.sock");
        }
    }

    @Test
    void allFailedSourcesAreDown() {
        var client = mock(DockerClient.class);
        when(client.pingCmd()).thenThrow(new RuntimeException("secret"));
        try (var health = new DockerHealthIndicator(
                new DockerJavaOperations(new DockerSourceRegistry(List.of(source("a", client)))))) {
            assertThat(health.health().getStatus()).hasToString("DOWN");
        }
    }

    @Test
    void repeatedRequestsReuseOneSlowProbeAndRemainBounded() {
        var client = mock(DockerClient.class);
        var ping = mock(PingCmd.class);
        when(client.pingCmd()).thenReturn(ping);
        var count = new AtomicInteger();
        var release = new CountDownLatch(1);
        doAnswer(i -> {
                    count.incrementAndGet();
                    release.await(5, TimeUnit.SECONDS);
                    return null;
                })
                .when(ping)
                .exec();
        try (var health = new DockerHealthIndicator(
                new DockerJavaOperations(new DockerSourceRegistry(List.of(source("a", client)))),
                Duration.ofMillis(30))) {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                health.health();
                health.health();
            });
            assertThat(count.get()).isEqualTo(1);
        } finally {
            release.countDown();
        }
    }
}
