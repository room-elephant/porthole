package com.roomelephant.porthole.core.version;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.core.model.exception.NotFoundException;
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

    @Test
    void cacheReadersReuseTheProjectedResultWithoutRepeatingReleaseSelection() {
        var id = new com.roomelephant.porthole.core.model.ContainerKey("local", "a");
        var snapshot = mock(VersionObservation.class);
        when(snapshot.toVersionInfo()).thenReturn(old);
        when(resolver.resolve(eq(id), nullable(VersionObservation.class))).thenReturn(snapshot);
        service.getCachedVersionAndRefresh(id);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(id)).isEqualTo(old);
        assertThat(service.getCachedVersionAndRefresh(id)).isEqualTo(old);
        verify(snapshot, times(1)).toVersionInfo();
    }

    private static VersionObservation observation(VersionInfo info) {
        String a = "sha256:" + "a".repeat(64), b = "sha256:" + "b".repeat(64);
        var metadata = new com.roomelephant.porthole.core.model.ManifestMetadata(
                info.updateAvailable() ? b : a, com.roomelephant.porthole.core.model.ManifestMetadata.Kind.INDEX);
        var manifests = new com.roomelephant.porthole.core.model.ManifestEvidence(
                new com.roomelephant.porthole.core.model.RegistryLookup.Available<>(metadata),
                Map.of(
                        a,
                        new com.roomelephant.porthole.core.model.RegistryLookup.Available<>(
                                new com.roomelephant.porthole.core.model.ManifestMetadata(a, metadata.kind()))));
        return new VersionObservation(
                "nginx:1.0",
                a,
                info.currentVersion(),
                List.of(a),
                info.latestVersion() == null
                        ? null
                        : new com.roomelephant.porthole.core.model.TagCatalogue(List.of(info.latestVersion())),
                manifests);
    }

    @BeforeEach
    void setUp() {
        when(docker.listContainersCmd()).thenReturn(list);
        when(list.withShowAll(true)).thenReturn(list);
        when(list.exec()).thenReturn(List.of());
        service = new VersionRefreshService(
                com.roomelephant.porthole.DockerTestSources.operationsOf(docker),
                resolver,
                new VersionRefreshSettings(Duration.ofHours(1)),
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
        var id = new com.roomelephant.porthole.core.model.ContainerKey("local", "a");
        when(resolver.resolve(eq(id), nullable(VersionObservation.class)))
                .thenReturn(observation(old), observation(new VersionInfo("1.0", "3.0", true)));
        service.refreshSource("local");
        workers.runAll();
        service.requestRefresh();
        service.requestRefresh();
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
        var dockerSources = new com.roomelephant.porthole.outbound.docker.DockerSourceRegistry(List.of(
                new com.roomelephant.porthole.outbound.docker.DockerConnection(
                        "local", "Local", null, Duration.ofSeconds(5), Duration.ofSeconds(60), docker),
                new com.roomelephant.porthole.outbound.docker.DockerConnection(
                        "remote", "Remote", null, Duration.ofSeconds(5), Duration.ofSeconds(60), remoteDocker)));
        var freshWorkers = new ManualExecutor();
        var freshCoordinators = new ManualExecutor();
        service = new VersionRefreshService(
                new com.roomelephant.porthole.outbound.docker.DockerJavaOperations(dockerSources),
                resolver,
                new VersionRefreshSettings(Duration.ofHours(1)),
                clock,
                freshWorkers,
                scheduler,
                freshCoordinators);
        var discovered = mock(Container.class);
        when(discovered.getId()).thenReturn("a");
        when(list.exec()).thenThrow(new IllegalStateException("source unavailable"));
        when(remoteList.exec()).thenReturn(List.of(discovered));
        var remote = new com.roomelephant.porthole.core.model.ContainerKey("remote", "a");
        when(resolver.resolve(eq(remote), nullable(VersionObservation.class))).thenReturn(observation(old));
        service.requestRefresh();
        assertThat(freshCoordinators.tasks).hasSize(1);
        verify(docker, never()).listContainersCmd();
        freshCoordinators.runAll();
        freshWorkers.runAll();
        assertThat(service.getCachedVersionAndRefresh(remote)).isEqualTo(old);
        service.requestRefresh();
        assertThat(freshCoordinators.tasks).hasSize(1);
    }

    @Test
    void manualRequestDoesNotInvalidateCacheOnRequestThread() {
        service.requestRefresh();
        verify(resolver, never()).invalidateRegistryCache();
    }

    @Test
    void manualRequestForcesRecentlyCheckedContainersDuringAnActiveScan() {
        inventory("a");
        var a = new com.roomelephant.porthole.core.model.ContainerKey("local", "a");
        when(resolver.resolve(eq(a), nullable(VersionObservation.class)))
                .thenReturn(observation(old), observation(new VersionInfo("1.0", "3.0", true)));
        service.refreshSource("local");
        workers.runAll();
        inventory("a", "b");
        service.refreshSource("local");
        assertThat(workers.tasks).hasSize(1);
        service.requestRefresh();
        coordinators.runAll();
        assertThat(workers.tasks).hasSize(2);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(a).latestVersion()).isEqualTo("3.0");
    }

    @Test
    void olderScheduledInventoryCannotPruneNewerForcedDiscovery() {
        var discovered = mock(Container.class);
        when(discovered.getId()).thenReturn("new");
        var id = new com.roomelephant.porthole.core.model.ContainerKey("local", "new");
        when(resolver.resolve(eq(id), nullable(VersionObservation.class))).thenReturn(observation(old));
        when(list.exec())
                .thenAnswer(_ -> {
                    service.requestRefresh();
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
        service.requestRefresh();
        coordinators.runAll();
        assertThat(workers.tasks).hasSize(1);
    }

    @Test
    void successfulScanPrunesOnlyItsSource() {
        var local = new com.roomelephant.porthole.core.model.ContainerKey("local", "same-id");
        var remote = new com.roomelephant.porthole.core.model.ContainerKey("mike", "same-id");
        when(resolver.resolve(eq(local), nullable(VersionObservation.class))).thenReturn(observation(old));
        when(resolver.resolve(eq(remote), nullable(VersionObservation.class))).thenReturn(observation(old));
        service.getCachedVersionAndRefresh(local);
        service.getCachedVersionAndRefresh(remote);
        workers.runAll();
        service.refreshSource("local");
        assertThat(service.getCachedVersionAndRefresh(local)).isNull();
        assertThat(service.getCachedVersionAndRefresh(remote)).isEqualTo(old);
    }

    @Test
    void sameContainerIdOnTwoSourcesHasIndependentVersions() {
        var a = new com.roomelephant.porthole.core.model.ContainerKey("local", "same-id");
        var b = new com.roomelephant.porthole.core.model.ContainerKey("mike", "same-id");
        when(resolver.resolve(eq(a), nullable(VersionObservation.class))).thenReturn(observation(old));
        when(resolver.resolve(eq(b), nullable(VersionObservation.class)))
                .thenReturn(observation(new VersionInfo("9", null, false)));
        assertThat(service.getCachedVersionAndRefresh(a)).isNull();
        assertThat(service.getCachedVersionAndRefresh(b)).isNull();
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(a)).isEqualTo(old);
        assertThat(service.getCachedVersionAndRefresh(b).currentVersion()).isEqualTo("9");
    }

    @Test
    void missesQueueOneCheckAndPublishItsResult() {
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")),
                        nullable(VersionObservation.class)))
                .thenReturn(observation(old));
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isNull();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isNull();
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
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
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")),
                        nullable(VersionObservation.class)))
                .thenReturn(observation(old), observation(new VersionInfo("1.0", "3.0", true)));
        var first = service.refreshSource("local");
        workers.runAll();
        assertThat(first).isCompleted();
        clock.advance(Duration.ofHours(1));
        var second = service.refreshSource("local");
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isEqualTo(old);
        assertThat(service.refreshSource("local")).isSameAs(second);
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                                new com.roomelephant.porthole.core.model.ContainerKey("local", "a"))
                        .latestVersion())
                .isEqualTo("3.0");
    }

    @Test
    void failedRefreshKeepsSuccessfulResultAndOtherContainersProgress() {
        inventory("a");
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")),
                        nullable(VersionObservation.class)))
                .thenReturn(observation(old));
        service.refreshSource("local");
        workers.runAll();
        clock.advance(Duration.ofHours(1));
        inventory("a", "b");
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")),
                        nullable(VersionObservation.class)))
                .thenThrow(new IllegalStateException("registry unavailable"));
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "b")),
                        nullable(VersionObservation.class)))
                .thenReturn(observation(new VersionInfo("5.0", null, false)));
        var refresh = service.refreshSource("local");
        workers.runAll();
        assertThat(refresh).isCompleted();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isEqualTo(old);
        assertThat(service.getCachedVersionAndRefresh(
                                new com.roomelephant.porthole.core.model.ContainerKey("local", "b"))
                        .currentVersion())
                .isEqualTo("5.0");
    }

    @Test
    void failedFirstCheckIsRetriedOnlyAfterConfiguredInterval() {
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")),
                        nullable(VersionObservation.class)))
                .thenThrow(new IllegalStateException("outage"))
                .thenReturn(observation(old));
        service.getCachedVersionAndRefresh(new com.roomelephant.porthole.core.model.ContainerKey("local", "a"));
        workers.runAll();
        clock.advance(Duration.ofMinutes(59));
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isNull();
        assertThat(workers.tasks).isEmpty();
        clock.advance(Duration.ofMinutes(1));
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isNull();
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isEqualTo(old);
    }

    @Test
    void inventoryFailureKeepsCacheAndDoesNotStopLaterScans() {
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")),
                        nullable(VersionObservation.class)))
                .thenReturn(observation(old));
        service.getCachedVersionAndRefresh(new com.roomelephant.porthole.core.model.ContainerKey("local", "a"));
        workers.runAll();
        when(list.exec())
                .thenThrow(new IllegalStateException("docker unavailable"))
                .thenReturn(List.of());
        assertThat(service.refreshSource("local")).isCompleted();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isEqualTo(old);
        service.refreshSource("local");
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isNull();
    }

    @Test
    void prunedQueuedWorkCannotRestoreRemovedResult() {
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")),
                        nullable(VersionObservation.class)))
                .thenReturn(observation(old));
        service.getCachedVersionAndRefresh(new com.roomelephant.porthole.core.model.ContainerKey("local", "a"));
        service.refreshSource("local");
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isNull();
    }

    @Test
    void observationDuringScanSurvivesOlderInventory() {
        when(list.exec()).thenAnswer(call -> {
            service.getCachedVersionAndRefresh(new com.roomelephant.porthole.core.model.ContainerKey("local", "new"));
            return List.of();
        });
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "new")),
                        nullable(VersionObservation.class)))
                .thenReturn(observation(old));
        service.refreshSource("local");
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "new")))
                .isEqualTo(old);
    }

    @Test
    void lateRunningResultCannotRestorePrunedEntry() {
        service.getCachedVersionAndRefresh(new com.roomelephant.porthole.core.model.ContainerKey("local", "a"));
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")),
                        nullable(VersionObservation.class)))
                .thenAnswer(call -> {
                    service.refreshSource("local");
                    return observation(old);
                });
        workers.runAll();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
                .isNull();
    }

    @Test
    void disappearingContainerDoesNotStopBatch() {
        inventory("a", "b");
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "a")),
                        nullable(VersionObservation.class)))
                .thenThrow(new NotFoundException("a"));
        when(resolver.resolve(
                        eq(new com.roomelephant.porthole.core.model.ContainerKey("local", "b")),
                        nullable(VersionObservation.class)))
                .thenReturn(observation(old));
        var refresh = service.refreshSource("local");
        workers.runAll();
        assertThat(refresh).isCompleted();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "b")))
                .isEqualTo(old);
    }

    @Test
    void closeStopsResourcesAndPreventsNewChecks() {
        service.close();
        assertThat(service.getCachedVersionAndRefresh(
                        new com.roomelephant.porthole.core.model.ContainerKey("local", "a")))
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
        when(resolver.resolve(
                        any(com.roomelephant.porthole.core.model.ContainerKey.class),
                        nullable(VersionObservation.class)))
                .thenAnswer(call -> {
                    entered.incrementAndGet();
                    running.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker did not release");
                    return observation(old);
                });
        try (var actual = new VersionRefreshService(
                com.roomelephant.porthole.DockerTestSources.operationsOf(docker),
                resolver,
                new VersionRefreshSettings(Duration.ofHours(1)))) {
            for (int i = 0; i < 8; i++)
                assertThat(actual.getCachedVersionAndRefresh(
                                new com.roomelephant.porthole.core.model.ContainerKey("local", "c" + i)))
                        .isNull();
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(entered.get()).isEqualTo(4);
            assertTimeoutPreemptively(
                    Duration.ofSeconds(1),
                    () -> assertThat(actual.getCachedVersionAndRefresh(
                                    new com.roomelephant.porthole.core.model.ContainerKey("local", "c0")))
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
