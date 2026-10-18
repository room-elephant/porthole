package com.roomelephant.porthole.core.enrichment.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.version.*;
import com.roomelephant.porthole.core.version.VersionRefreshSettings;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class VersionEnricherTest {
    private final EnrichmentContext context = new EnrichmentContext("local", "localhost", null, false, false);

    private Container container() {
        return new Container(
                "a",
                "local",
                "app",
                "app",
                "nginx:1.0",
                new Container.Details(Set.of(), Map.of(), "icon", null, "running", "Up"),
                List.of());
    }

    @Test
    void attachesCachedValueToTheSameContainer() {
        var cache = mock(VersionRefreshService.class);
        var version = new VersionInfo("1.0", "2.0", true);
        when(cache.getCachedVersionAndRefresh(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .thenReturn(version);
        var container = container();
        assertThat(new VersionEnricher(cache).enrich(container, context)).containsSame(container);
        assertThat(container.version()).isEqualTo(version);
    }

    @Test
    void missingVersionDoesNotWaitForSlowResolution() throws Exception {
        var docker = mock(com.roomelephant.porthole.core.port.outbound.DockerOperations.class);
        var resolver =
                new VersionService(docker, mock(com.roomelephant.porthole.core.port.outbound.RegistryOperations.class));
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(docker.inspectContainer(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .thenAnswer(call -> {
                    running.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    return new ContainerInspection(null, null, List.of(), Map.of());
                });
        try (var cache = new VersionRefreshService(docker, resolver, new VersionRefreshSettings(Duration.ofHours(1)))) {
            var enricher = new VersionEnricher(cache);
            var container = container();
            assertTimeoutPreemptively(
                    Duration.ofSeconds(1),
                    () -> assertThat(enricher.enrich(container, context)).containsSame(container));
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(container.version()).isNull();
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> enricher.enrich(container, context));
        } finally {
            release.countDown();
        }
    }
}
