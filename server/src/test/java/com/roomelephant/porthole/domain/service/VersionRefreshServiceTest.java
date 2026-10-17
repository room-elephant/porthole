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
    private final ManualExecutor coordinators = new ManualExecutor();
    private final MutableClock clock = new MutableClock();
    private final VersionInfo old = new VersionInfo("1.0", "2.0", true);
    private VersionRefreshService service;

    @BeforeEach
    void setUp() {
        when(docker.listContainersCmd()).thenReturn(list);
        when(list.withShowAll(true)).thenReturn(list);
        when(list.exec()).thenReturn(List.of());
        service = new VersionRefreshService(
                com.roomelephant.porthole.DockerTestSources.of(docker),
                resolver,
                new VersionProperties(Duration.ofHours(1)),
                clock,
                workers,
                scheduler,
                coordinators);
    }

    @AfterEach
    void close() {
        service.close();
    }

    @Test
    void forcedRefreshBypassesIntervalAndKeepsOldResultWhileChecking() {
        inventory("a");
        var id = new com.roomelephant.porthole.domain.model.ContainerKey("local", "a");
        when(resolver.getVersionInfo(id)).thenReturn(old, new VersionInfo("1.0", "3.0", true));
        service.refreshSource("local");
        workers.runAll();
        service.forceRefresh();
        service.forceRefresh();
        assertThat(coordinators.tasks).hasSize(1);
        coordinators.runAll();
        verify(resolver).invalidateRegistryCache();
        assertThat(service.getCachedVersionAndRefresh(id)).isEqualTo(old);
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(id).latestVersion()).isEqualTo("3.0");
    }

    @Test
    void forcedRefreshChecksAllSourcesAndRecoversFromSourceFailure() {
        service.close();
        var remoteDocker = mock(DockerClient.class);
        var remoteList = mock(ListContainersCmd.class);
        when(remoteDocker.listContainersCmd()).thenReturn(remoteList);
        when(remoteList.withShowAll(true)).thenReturn(remoteList);
        var dockerSources = new com.roomelephant.porthole.domain.component.DockerSourceRegistry(List.of(
                new com.roomelephant.porthole.domain.model.DockerSource(
                        "local", "Local", null, Duration.ofSeconds(5), Duration.ofSeconds(60), docker),
                new com.roomelephant.porthole.domain.model.DockerSource(
                        "remote", "Remote", null, Duration.ofSeconds(5), Duration.ofSeconds(60), remoteDocker)));
        var freshWorkers = new ManualExecutor();
        var freshCoordinators = new ManualExecutor();
        service = new VersionRefreshService(
                dockerSources,
                resolver,
                new VersionProperties(Duration.ofHours(1)),
                clock,
                freshWorkers,
                scheduler,
                freshCoordinators);
        var discovered = mock(Container.class);
        when(discovered.getId()).thenReturn("a");
        when(list.exec()).thenThrow(new IllegalStateException("source unavailable"));
        when(remoteList.exec()).thenReturn(List.of(discovered));
        var remote = new com.roomelephant.porthole.domain.model.ContainerKey("remote", "a");
        when(resolver.getVersionInfo(remote)).thenReturn(old);
        service.forceRefresh();
        assertThat(freshCoordinators.tasks).hasSize(1);
        verify(docker, never()).listContainersCmd();
        freshCoordinators.runAll();
        freshWorkers.runAll();
        assertThat(service.getCachedVersionAndRefresh(remote)).isEqualTo(old);
        service.forceRefresh();
        assertThat(freshCoordinators.tasks).hasSize(1);
    }

    @Test
    void manualRequestDoesNotInvalidateCacheOnRequestThread() {
        service.forceRefresh();
        verify(resolver, never()).invalidateRegistryCache();
    }

    @Test
    void manualRequestForcesRecentlyCheckedContainersDuringAnActiveScan() {
        inventory("a");
        var a = new com.roomelephant.porthole.domain.model.ContainerKey("local", "a");
        when(resolver.getVersionInfo(a)).thenReturn(old, new VersionInfo("1.0", "3.0", true));
        service.refreshSource("local");
        workers.runAll();
        inventory("a", "b");
        service.refreshSource("local");
        assertThat(workers.tasks).hasSize(1);
        service.forceRefresh();
        coordinators.runAll();
        assertThat(workers.tasks).hasSize(2);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(a).latestVersion()).isEqualTo("3.0");
    }

    @Test
    void olderScheduledInventoryCannotPruneNewerForcedDiscovery() {
        var discovered = mock(Container.class);
        when(discovered.getId()).thenReturn("new");
        var id = new com.roomelephant.porthole.domain.model.ContainerKey("local", "new");
        when(resolver.getVersionInfo(id)).thenReturn(old);
        when(list.exec())
                .thenAnswer(_ -> {
                    service.forceRefresh();
                    coordinators.runAll();
                    return List.of();
                })
                .thenReturn(List.of(discovered));
        service.refreshSource("local");
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(id)).isEqualTo(old);
    }

    @Test
    void forcedRefreshReusesActiveContainerCheck() {
        inventory("a");
        service.refreshSource("local");
        service.forceRefresh();
        coordinators.runAll();
        assertThat(workers.tasks).hasSize(1);
    }

    @Test
    void successfulScanPrunesOnlyItsSource() {
        var local = new com.roomelephant.porthole.domain.model.ContainerKey("local", "same-id");
        var remote = new com.roomelephant.porthole.domain.model.ContainerKey("mike", "same-id");
        when(resolver.getVersionInfo(local)).thenReturn(old);
        when(resolver.getVersionInfo(remote)).thenReturn(old);
        service.getCachedVersionAndRefresh(local);
        service.getCachedVersionAndRefresh(remote);
        workers.runAll();
        service.refreshSource("local");
        assertThat(service.getCachedVersionAndRefresh(local)).isNull();
        assertThat(service.getCachedVersionAndRefresh(remote)).isEqualTo(old);
    }

    @Test
    void sameContainerIdOnTwoSourcesHasIndependentVersions() {
        var a = new com.roomelephant.porthole.domain.model.ContainerKey("local", "same-id");
        var b = new com.roomelephant.porthole.domain.model.ContainerKey("mike", "same-id");
        when(resolver.getVersionInfo(a)).thenReturn(old);
        when(resolver.getVersionInfo(b)).thenReturn(new VersionInfo("9", null, false));
        assertThat(service.getCachedVersionAndRefresh(a)).isNull();
        assertThat(service.getCachedVersionAndRefresh(b)).isNull();
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(a)).isEqualTo(old);
        assertThat(service.getCachedVersionAndRefresh(b).currentVersion()).isEqualTo("9");
    }

    @Test
    void missesQueueOneCheckAndPublishItsResult() {
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .thenReturn(old);
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isNull();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isNull();
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isEqualTo(old);
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
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .thenReturn(old, new VersionInfo("1.0", "3.0", true));
        var first = service.refreshSource("local");
        workers.runAll();
        assertThat(first).isCompleted();
        clock.advance(Duration.ofHours(1));
        var second = service.refreshSource("local");
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isEqualTo(old);
        assertThat(service.refreshSource("local")).isSameAs(second);
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                                new com.roomelephant.porthole.domain.model.ContainerKey("local", "a"))
                        .latestVersion())
                .isEqualTo("3.0");
    }

    @Test
    void failedRefreshKeepsSuccessfulResultAndOtherContainersProgress() {
        inventory("a");
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .thenReturn(old);
        service.refreshSource("local");
        workers.runAll();
        clock.advance(Duration.ofHours(1));
        inventory("a", "b");
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .thenThrow(new IllegalStateException("registry unavailable"));
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "b")))
                .thenReturn(new VersionInfo("5.0", null, false));
        var refresh = service.refreshSource("local");
        workers.runAll();
        assertThat(refresh).isCompleted();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isEqualTo(old);
        assertThat(service.getCachedVersionAndRefresh(
                                new com.roomelephant.porthole.domain.model.ContainerKey("local", "b"))
                        .currentVersion())
                .isEqualTo("5.0");
    }

    @Test
    void failedFirstCheckIsRetriedOnlyAfterConfiguredInterval() {
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .thenThrow(new IllegalStateException("outage"))
                .thenReturn(old);
        service.getCachedVersionAndRefresh(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a"));
        workers.runAll();
        clock.advance(Duration.ofMinutes(59));
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isNull();
        assertThat(workers.tasks).isEmpty();
        clock.advance(Duration.ofMinutes(1));
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isNull();
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isEqualTo(old);
    }

    @Test
    void inventoryFailureKeepsCacheAndDoesNotStopLaterScans() {
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .thenReturn(old);
        service.getCachedVersionAndRefresh(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a"));
        workers.runAll();
        when(list.exec())
                .thenThrow(new IllegalStateException("docker unavailable"))
                .thenReturn(List.of());
        assertThat(service.refreshSource("local")).isCompleted();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isEqualTo(old);
        service.refreshSource("local");
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isNull();
    }

    @Test
    void prunedQueuedWorkCannotRestoreRemovedResult() {
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .thenReturn(old);
        service.getCachedVersionAndRefresh(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a"));
        service.refreshSource("local");
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isNull();
    }

    @Test
    void observationDuringScanSurvivesOlderInventory() {
        when(list.exec()).thenAnswer(call -> {
            service.getCachedVersionAndRefresh(new com.roomelephant.porthole.domain.model.ContainerKey("local", "new"));
            return List.of();
        });
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "new")))
                .thenReturn(old);
        service.refreshSource("local");
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "new")))
                .isEqualTo(old);
    }

    @Test
    void lateRunningResultCannotRestorePrunedEntry() {
        service.getCachedVersionAndRefresh(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a"));
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .thenAnswer(call -> {
                    service.refreshSource("local");
                    return old;
                });
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isNull();
    }

    @Test
    void disappearingContainerDoesNotStopBatch() {
        inventory("a", "b");
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .thenThrow(new NotFoundException("a"));
        when(resolver.getVersionInfo(new com.roomelephant.porthole.domain.model.ContainerKey("local", "b")))
                .thenReturn(old);
        var refresh = service.refreshSource("local");
        workers.runAll();
        assertThat(refresh).isCompleted();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "b")))
                .isEqualTo(old);
    }

    @Test
    void closeStopsResourcesAndPreventsNewChecks() {
        service.close();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.domain.model.ContainerKey("local", "a")))
                .isNull();
        assertThat(workers.isShutdown()).isTrue();
        assertThat(coordinators.isShutdown()).isTrue();
        assertThat(workers.tasks).isEmpty();
        verify(scheduler).shutdownNow();
    }

    @Test
    void productionWorkersBoundConcurrencyAndReadsDoNotWait() throws Exception {
        var running = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var entered = new java.util.concurrent.atomic.AtomicInteger();
        when(resolver.getVersionInfo(any(com.roomelephant.porthole.domain.model.ContainerKey.class)))
                .thenAnswer(call -> {
                    entered.incrementAndGet();
                    running.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker did not release");
                    return old;
                });
        try (var actual = new VersionRefreshService(
                com.roomelephant.porthole.DockerTestSources.of(docker),
                resolver,
                new VersionProperties(Duration.ofHours(1)))) {
            for (int i = 0; i < 8; i++)
                assertThat(actual.getCachedVersionAndRefresh(
                                new com.roomelephant.porthole.domain.model.ContainerKey("local", "c" + i)))
                        .isNull();
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(entered.get()).isEqualTo(4);
            assertTimeoutPreemptively(
                    Duration.ofSeconds(1),
                    () -> assertThat(actual.getCachedVersionAndRefresh(
                                    new com.roomelephant.porthole.domain.model.ContainerKey("local", "c0")))
                            .isNull());
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
