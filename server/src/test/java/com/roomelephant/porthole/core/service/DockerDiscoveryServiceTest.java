package com.roomelephant.porthole.core.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class DockerDiscoveryServiceTest {
    private final DockerOperations docker = mock(DockerOperations.class);

    private DockerSource source(String id) {
        return new DockerSource(id, id, null, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    void startsSourcesConcurrentlyAndReturnsThemInConfigurationOrder() {
        var firstStarted = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var first = source("first");
        var second = source("second");
        var snapshot = new ContainerSnapshot("a", "app", "nginx:1", Set.of(), Map.of(), "running", "Up");
        when(docker.sources()).thenReturn(List.of(first, second));
        when(docker.listContainers("first")).thenAnswer(_ -> {
            firstStarted.countDown();
            if (!secondStarted.await(1, TimeUnit.SECONDS)) throw new IllegalStateException("Second source was blocked");
            return List.of(snapshot);
        });
        when(docker.listContainers("second")).thenAnswer(_ -> {
            secondStarted.countDown();
            if (!firstStarted.await(1, TimeUnit.SECONDS)) throw new IllegalStateException("First source was blocked");
            return List.of();
        });
        try (var discovery = new DockerDiscoveryService(docker)) {
            var result = discovery.discover();
            assertThat(result).extracting(r -> r.source().id()).containsExactly("first", "second");
            assertThat(result).extracting(r -> r.state().status()).containsExactly(SourceStatus.UP, SourceStatus.UP);
            assertThat(result.getFirst().containers()).containsExactly(snapshot);
            assertThat(result.getLast().containers()).isEmpty();
        }
    }

    @Test
    void closingDiscoveryReleasesCallersAndInterruptsDockerWork() throws Exception {
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(docker.sources()).thenReturn(List.of(source("local")));
        when(docker.listContainers("local")).thenAnswer(_ -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                throw e;
            }
            return List.of();
        });
        try (var discovery = new DockerDiscoveryService(docker);
                var caller = Executors.newVirtualThreadPerTaskExecutor()) {
            var response = caller.submit(discovery::discover);
            try {
                assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
                discovery.close();
                assertThatThrownBy(() -> response.get(1, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasRootCauseInstanceOf(IllegalStateException.class);
                assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
        }
    }
}
