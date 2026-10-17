package com.roomelephant.porthole.domain.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.roomelephant.porthole.config.properties.VersionProperties;
import com.roomelephant.porthole.domain.model.VersionInfo;
import com.roomelephant.porthole.domain.model.exception.NotFoundException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class VersionRefreshServiceTest {
    private final DockerClient docker = mock(DockerClient.class);
    private final ListContainersCmd list = mock(ListContainersCmd.class);
    private final VersionService resolver = mock(VersionService.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final ManualExecutor workers = new ManualExecutor();
    private final MutableClock clock = new MutableClock();
    private final VersionInfo old = new VersionInfo("1.0", "2.0", true);
    private VersionRefreshService service;

    @BeforeEach
    void setUp() {
        when(docker.listContainersCmd()).thenReturn(list);
        when(list.withShowAll(true)).thenReturn(list);
        when(list.exec()).thenReturn(List.of());
        service = new VersionRefreshService(
                docker, resolver, new VersionProperties(Duration.ofHours(1)), clock, workers, scheduler);
    }

    @AfterEach
    void close() {
        service.close();
    }

    @Test
    void missesQueueOneCheckAndPublishItsResult() {
        when(resolver.getVersionInfo("a")).thenReturn(old);
        assertThat(service.getCachedVersionAndRefresh("a")).isNull();
        assertThat(service.getCachedVersionAndRefresh("a")).isNull();
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh("a")).isEqualTo(old);
        assertThat(workers.tasks).isEmpty();
    }

    @Test
    void readinessQueuesImmediateFixedDelayRefreshWithoutCallingDocker() {
        service.start();
        var job = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler)
                .scheduleWithFixedDelay(
                        job.capture(), eq(0L), eq(Duration.ofHours(1).toNanos()), eq(TimeUnit.NANOSECONDS));
        verify(docker, never()).listContainersCmd();
        job.getValue().run();
        verify(list).withShowAll(true);
    }

    @Test
    void refreshKeepsOldValueUntilReplacementAndCoalescesBatches() {
        inventory("a");
        when(resolver.getVersionInfo("a")).thenReturn(old, new VersionInfo("1.0", "3.0", true));
        var first = service.refreshAll();
        workers.runAll();
        assertThat(first).isCompleted();
        clock.advance(Duration.ofHours(1));
        var second = service.refreshAll();
        assertThat(service.getCachedVersionAndRefresh("a")).isEqualTo(old);
        assertThat(service.refreshAll()).isSameAs(second);
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh("a").latestVersion()).isEqualTo("3.0");
    }

    @Test
    void failedRefreshKeepsSuccessfulResultAndOtherContainersProgress() {
        inventory("a");
        when(resolver.getVersionInfo("a")).thenReturn(old);
        service.refreshAll();
        workers.runAll();
        clock.advance(Duration.ofHours(1));
        inventory("a", "b");
        when(resolver.getVersionInfo("a")).thenThrow(new IllegalStateException("registry unavailable"));
        when(resolver.getVersionInfo("b")).thenReturn(new VersionInfo("5.0", null, false));
        var refresh = service.refreshAll();
        workers.runAll();
        assertThat(refresh).isCompleted();
        assertThat(service.getCachedVersionAndRefresh("a")).isEqualTo(old);
        assertThat(service.getCachedVersionAndRefresh("b").currentVersion()).isEqualTo("5.0");
    }

    @Test
    void failedFirstCheckIsRetriedOnlyAfterConfiguredInterval() {
        when(resolver.getVersionInfo("a"))
                .thenThrow(new IllegalStateException("outage"))
                .thenReturn(old);
        service.getCachedVersionAndRefresh("a");
        workers.runAll();
        clock.advance(Duration.ofMinutes(59));
        assertThat(service.getCachedVersionAndRefresh("a")).isNull();
        assertThat(workers.tasks).isEmpty();
        clock.advance(Duration.ofMinutes(1));
        assertThat(service.getCachedVersionAndRefresh("a")).isNull();
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh("a")).isEqualTo(old);
    }

    @Test
    void inventoryFailureKeepsCacheAndDoesNotStopLaterScans() {
        when(resolver.getVersionInfo("a")).thenReturn(old);
        service.getCachedVersionAndRefresh("a");
        workers.runAll();
        when(list.exec())
                .thenThrow(new IllegalStateException("docker unavailable"))
                .thenReturn(List.of());
        assertThat(service.refreshAll()).isCompleted();
        assertThat(service.getCachedVersionAndRefresh("a")).isEqualTo(old);
        service.refreshAll();
        assertThat(service.getCachedVersionAndRefresh("a")).isNull();
    }

    @Test
    void prunedQueuedWorkCannotRestoreRemovedResult() {
        when(resolver.getVersionInfo("a")).thenReturn(old);
        service.getCachedVersionAndRefresh("a");
        service.refreshAll();
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh("a")).isNull();
    }

    @Test
    void observationDuringScanSurvivesOlderInventory() {
        when(list.exec()).thenAnswer(call -> {
            service.getCachedVersionAndRefresh("new");
            return List.of();
        });
        when(resolver.getVersionInfo("new")).thenReturn(old);
        service.refreshAll();
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh("new")).isEqualTo(old);
    }

    @Test
    void lateRunningResultCannotRestorePrunedEntry() {
        service.getCachedVersionAndRefresh("a");
        when(resolver.getVersionInfo("a")).thenAnswer(call -> {
            service.refreshAll();
            return old;
        });
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh("a")).isNull();
    }

    @Test
    void disappearingContainerDoesNotStopBatch() {
        inventory("a", "b");
        when(resolver.getVersionInfo("a")).thenThrow(new NotFoundException("a"));
        when(resolver.getVersionInfo("b")).thenReturn(old);
        var refresh = service.refreshAll();
        workers.runAll();
        assertThat(refresh).isCompleted();
        assertThat(service.getCachedVersionAndRefresh("b")).isEqualTo(old);
    }

    @Test
    void closeStopsResourcesAndPreventsNewChecks() {
        service.close();
        assertThat(service.getCachedVersionAndRefresh("a")).isNull();
        assertThat(workers.isShutdown()).isTrue();
        assertThat(workers.tasks).isEmpty();
        verify(scheduler).shutdownNow();
    }

    @Test
    void productionWorkersBoundConcurrencyAndReadsDoNotWait() throws Exception {
        var running = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var entered = new java.util.concurrent.atomic.AtomicInteger();
        when(resolver.getVersionInfo(anyString())).thenAnswer(call -> {
            entered.incrementAndGet();
            running.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker did not release");
            return old;
        });
        try (var actual = new VersionRefreshService(docker, resolver, new VersionProperties(Duration.ofHours(1)))) {
            for (int i = 0; i < 8; i++)
                assertThat(actual.getCachedVersionAndRefresh("c" + i)).isNull();
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(entered.get()).isEqualTo(4);
            assertTimeoutPreemptively(
                    Duration.ofSeconds(1),
                    () -> assertThat(actual.getCachedVersionAndRefresh("c0")).isNull());
            release.countDown();
        } finally {
            release.countDown();
        }
    }

    private static void assertTimeoutPreemptively(Duration duration, org.junit.jupiter.api.function.Executable action) {
        Assertions.assertTimeoutPreemptively(duration, action);
    }

    private void inventory(String... ids) {
        var containers = Arrays.stream(ids)
                .map(id -> {
                    var c = mock(Container.class);
                    when(c.getId()).thenReturn(id);
                    return c;
                })
                .toList();
        when(list.exec()).thenReturn(containers);
    }

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-16T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    static class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        boolean shutdown;

        @Override
        public void execute(Runnable task) {
            if (shutdown) throw new RejectedExecutionException();
            tasks.add(task);
        }

        void runAll() {
            while (!tasks.isEmpty()) tasks.remove().run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            var pending = List.copyOf(tasks);
            tasks.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
    }
}
