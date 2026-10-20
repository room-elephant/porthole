package com.roomelephant.porthole.core.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.core.model.DockerSource;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DockerAvailabilityServiceTest {
    private DockerOperations sources(String... ids) {
        var operations = mock(DockerOperations.class);
        when(operations.sources())
                .thenReturn(Arrays.stream(ids)
                        .map(id -> new DockerSource(id, id, null, Duration.ofSeconds(5), Duration.ofSeconds(60), true))
                        .toList());
        return operations;
    }

    @Test
    void returnsAvailabilityWithoutExposingDockerFailures() {
        var operations = sources("a", "b");
        doThrow(new RuntimeException("secret.sock")).when(operations).ping("b");
        try (var service = new DockerAvailabilityService(operations)) {
            var result = service.checkAvailability();
            assertThat(result.sources()).containsExactly(entry("a", true), entry("b", false));
            assertThat(result.anyAvailable()).isTrue();
            assertThat(result.toString()).doesNotContain("secret.sock");
        }
    }

    @Test
    void allFailedSourcesAreUnavailable() {
        var operations = sources("a");
        doThrow(new RuntimeException("secret")).when(operations).ping("a");
        try (var service = new DockerAvailabilityService(operations)) {
            var result = service.checkAvailability();
            assertThat(result.sources()).containsEntry("a", false);
            assertThat(result.anyAvailable()).isFalse();
        }
    }

    @Test
    void repeatedRequestsReuseOneSlowProbeAndRemainBounded() {
        var operations = sources("a");
        var count = new AtomicInteger();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(i -> {
                    count.incrementAndGet();
                    started.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    return null;
                })
                .when(operations)
                .ping("a");
        try (var service = new DockerAvailabilityService(operations, Duration.ofMillis(100))) {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                assertThat(service.checkAvailability().sources()).containsEntry("a", false);
                assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(service.checkAvailability().sources()).containsEntry("a", false);
            });
            assertThat(count.get()).isEqualTo(1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void probesSourcesConcurrently() {
        var operations = sources("a", "b");
        var started = new CountDownLatch(2);
        doAnswer(i -> {
                    started.countDown();
                    if (!started.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("Probes did not overlap");
                    return null;
                })
                .when(operations)
                .ping(anyString());
        try (var service = new DockerAvailabilityService(operations)) {
            assertThat(service.checkAvailability().sources()).containsExactly(entry("a", true), entry("b", true));
        }
    }

    @Test
    void closingServiceCancelsInFlightProbe() throws Exception {
        var operations = sources("a");
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        doAnswer(i -> {
                    started.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                        throw e;
                    }
                    return null;
                })
                .when(operations)
                .ping("a");
        var service = new DockerAvailabilityService(operations, Duration.ofMillis(100));
        try (service) {
            service.checkAvailability();
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(service.checkAvailability().anyAvailable()).isFalse();
    }
}
