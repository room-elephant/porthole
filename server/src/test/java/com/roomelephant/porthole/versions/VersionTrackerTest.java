package com.roomelephant.porthole.versions;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.DockerTestSources;
import com.roomelephant.porthole.fleet.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class VersionTrackerTest {
    private static final Duration INTERVAL = Duration.ofHours(1);
    private static final ContainerKey A = new ContainerKey("local", "a");

    private final VersionResolver resolver = mock(VersionResolver.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final ManualExecutor workers = new ManualExecutor();
    private final ManualExecutor coordinators = new ManualExecutor();
    private final MutableClock clock = new MutableClock();
    private final VersionInfo old = new VersionInfo("1.0", "2.0", true);
    private final VersionInfo fresh = new VersionInfo("1.0", "3.0", true);
    private VersionTracker service;

    @BeforeEach
    void setUp() {
        service = service(DockerTestSources.catalog("local", "mike"));
    }

    @AfterEach
    void close() {
        service.close();
    }

    private VersionTracker service(SourceCatalog catalog) {
        return new VersionTracker(
                catalog,
                resolver,
                new VersionProperties(INTERVAL),
                List.of(change -> {
                    notifications++;
                    changes.add(change);
                }),
                clock,
                workers,
                scheduler,
                coordinators);
    }

    private int notifications;
    private final List<VersionChange> changes = new ArrayList<>();

    private static Source source(String id) {
        return new Source(id, id, null, true, Duration.ofSeconds(1));
    }

    /** Each argument is {@code id} or {@code id@imageId}. */
    private static SourceSnapshot up(String sourceId, String... containers) {
        List<ContainerSnapshot> listed = Arrays.stream(containers)
                .map(spec -> {
                    String[] parts = spec.split("@", 2);
                    return new ContainerSnapshot(
                            parts[0],
                            parts[0],
                            "nginx:1",
                            parts.length > 1 ? parts[1] : "image",
                            Set.of(),
                            Map.of(),
                            "running",
                            "Up");
                })
                .toList();
        return SourceSnapshot.initial(source(sourceId)).succeeded(listed, Instant.EPOCH);
    }

    private static VersionObservation observation(VersionInfo info) {
        String a = "sha256:" + "a".repeat(64), b = "sha256:" + "b".repeat(64);
        var metadata = new com.roomelephant.porthole.versions.ManifestMetadata(
                info.updateAvailable() ? b : a, com.roomelephant.porthole.versions.ManifestMetadata.Kind.INDEX);
        var manifests = new com.roomelephant.porthole.versions.ManifestEvidence(
                new com.roomelephant.porthole.versions.RegistryLookup.Available<>(metadata),
                Map.of(
                        a,
                        new com.roomelephant.porthole.versions.RegistryLookup.Available<>(
                                new com.roomelephant.porthole.versions.ManifestMetadata(a, metadata.kind()))));
        return new VersionObservation(
                "nginx:1.0",
                a,
                info.currentVersion(),
                List.of(a),
                info.latestVersion() == null
                        ? null
                        : new com.roomelephant.porthole.versions.TagCatalogue(List.of(info.latestVersion())),
                manifests);
    }

    private void resolves(ContainerKey key, VersionInfo... results) {
        var stub = when(resolver.resolve(eq(key), nullable(VersionObservation.class)));
        for (VersionInfo result : results) stub = stub.thenReturn(observation(result));
    }

    @Test
    void newContainerInSnapshotQueuesCheck() {
        resolves(A, old);
        service.onSnapshot(up("local", "a"));
        assertThat(workers.tasks).hasSize(1);
        assertThat(service.cachedVersion(A)).isNull();
        workers.runAll();
        assertThat(service.cachedVersion(A)).isEqualTo(old);
    }

    @Test
    void cachedVersionReusesProjectedResultWithoutRepeatingReleaseSelection() {
        var observation = mock(VersionObservation.class);
        when(observation.toVersionInfo()).thenReturn(old);
        when(resolver.resolve(eq(A), nullable(VersionObservation.class))).thenReturn(observation);
        service.onSnapshot(up("local", "a"));
        workers.runAll();
        assertThat(service.cachedVersion(A)).isEqualTo(old);
        assertThat(service.cachedVersion(A)).isEqualTo(old);
        verify(observation, times(1)).toVersionInfo();
    }

    @Test
    void changedImageIdChecksImmediately() {
        resolves(A, old, fresh);
        service.onSnapshot(up("local", "a@one"));
        workers.runAll();
        service.onSnapshot(up("local", "a@two"));
        assertThat(workers.tasks).hasSize(1);
        assertThat(service.cachedVersion(A))
                .as("old result stays visible while checking")
                .isEqualTo(old);
        workers.runAll();
        assertThat(service.cachedVersion(A)).isEqualTo(fresh);
        verify(resolver, times(2)).resolve(eq(A), nullable(VersionObservation.class));
    }

    @Test
    void checkForReplacedImageCannotOverwriteNewerEntry() {
        when(resolver.resolve(eq(A), nullable(VersionObservation.class))).thenAnswer(_ -> {
            service.onSnapshot(up("local", "a@two"));
            return observation(old);
        });
        service.onSnapshot(up("local", "a@one"));
        workers.tasks.remove().run();
        assertThat(service.cachedVersion(A)).isNull();
        assertThat(workers.tasks).hasSize(1);
    }

    @Test
    void unchangedSnapshotDoesNotRecheckBeforeInterval() {
        resolves(A, old);
        service.onSnapshot(up("local", "a"));
        workers.runAll();
        service.onSnapshot(up("local", "a"));
        assertThat(workers.tasks).isEmpty();
    }

    @Test
    void failedSourceSnapshotNeitherPrunesNorChecks() {
        resolves(A, old);
        service.onSnapshot(up("local", "a"));
        workers.runAll();
        service.onSnapshot(SourceSnapshot.initial(source("local"))
                .failed(SourceSnapshot.Status.DOWN, "Docker is not reachable", Instant.EPOCH, INTERVAL));
        assertThat(service.cachedVersion(A)).isEqualTo(old);
        assertThat(workers.tasks).isEmpty();
    }

    @Test
    void upSnapshotPrunesMissingKeysOfThatSourceOnly() {
        var mike = new ContainerKey("mike", "a");
        resolves(A, old);
        resolves(mike, fresh);
        service.onSnapshot(up("local", "a"));
        service.onSnapshot(up("mike", "a"));
        workers.runAll();
        service.onSnapshot(up("local"));
        assertThat(service.cachedVersion(A)).isNull();
        assertThat(service.cachedVersion(mike)).isEqualTo(fresh);
    }

    @Test
    void readinessSchedulesOneFixedDelayScanPerSourceAfterAnInterval() {
        service.start();
        service.start();
        verify(scheduler, times(2))
                .scheduleWithFixedDelay(
                        any(Runnable.class), eq(INTERVAL.toNanos()), eq(INTERVAL.toNanos()), eq(TimeUnit.NANOSECONDS));
    }

    @Test
    void periodicScanUsesStoredSnapshotAndRespectsRetryTime() {
        resolves(A, old, fresh);
        service.onSnapshot(up("local", "a"));
        workers.runAll();
        assertThat(service.rescan("local", false)).isCompleted();
        assertThat(workers.tasks).isEmpty();
        clock.advance(INTERVAL);
        var scan = service.rescan("local", false);
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(scan).isCompleted();
        assertThat(service.cachedVersion(A)).isEqualTo(fresh);
    }

    @Test
    void scanWithoutUpSnapshotDoesNothing() {
        assertThat(service.rescan("local", true)).isCompleted();
        service.onSnapshot(SourceSnapshot.initial(source("local")));
        assertThat(service.rescan("local", true)).isCompleted();
        assertThat(workers.tasks).isEmpty();
    }

    @Test
    void startCheckForcesChecksAndCoalesces() {
        resolves(A, old, fresh);
        service.onSnapshot(up("local", "a"));
        workers.runAll();
        service.startCheck();
        service.startCheck();
        verify(resolver, never()).invalidateRegistryCache();
        assertThat(coordinators.tasks).hasSize(1);
        coordinators.runAll();
        verify(resolver).invalidateRegistryCache();
        assertThat(service.cachedVersion(A)).isEqualTo(old);
        assertThat(workers.tasks).hasSize(1);
        workers.runAll();
        assertThat(service.cachedVersion(A)).isEqualTo(fresh);
        service.startCheck();
        assertThat(coordinators.tasks).hasSize(1);
    }

    @Test
    void forcedRefreshReusesActiveContainerCheck() {
        service.onSnapshot(up("local", "a"));
        service.startCheck();
        coordinators.runAll();
        assertThat(workers.tasks).hasSize(1);
    }

    @Test
    void startCheckAfterCloseIsRejected() {
        service.close();
        assertThatThrownBy(service::startCheck).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void noRunHasCompletedAtStartup() {
        assertThat(service.checks()).isEqualTo(new VersionChecks(false, null));
    }

    @Test
    void notifiesWhenAResultChanges() {
        resolves(A, old, old, fresh);
        service.onSnapshot(up("local", "a"));
        workers.runAll();
        assertThat(notifications).isEqualTo(1);
        assertThat(changes).containsExactly(new VersionChange.Result(A, null, old));

        clock.advance(INTERVAL);
        service.rescan("local", false);
        workers.runAll();
        assertThat(notifications).as("same result").isEqualTo(1);

        clock.advance(INTERVAL);
        service.rescan("local", false);
        workers.runAll();
        assertThat(notifications).isEqualTo(2);
        assertThat(changes).last().isEqualTo(new VersionChange.Result(A, old, fresh));
    }

    @Test
    void aCheckRunsUntilEverySourceFinished() {
        resolves(A, old);
        service.onSnapshot(up("local", "a"));
        workers.runAll();
        notifications = 0;
        clock.advance(Duration.ofMinutes(5));

        service.startCheck();
        assertThat(service.checks()).isEqualTo(new VersionChecks(true, null));
        assertThat(notifications).isEqualTo(1);

        coordinators.runAll();
        assertThat(service.checks().running()).as("local still checking").isTrue();
        workers.runAll();

        assertThat(service.checks()).isEqualTo(new VersionChecks(false, clock.instant()));
        assertThat(notifications).isEqualTo(2);
        assertThat(changes)
                .endsWith(
                        new VersionChange.Run(new VersionChecks(true, null)),
                        new VersionChange.Run(new VersionChecks(false, clock.instant())));
    }

    @Test
    void aCheckWithNoSnapshotsStillCompletes() {
        service.startCheck();
        coordinators.runAll();

        assertThat(service.checks()).isEqualTo(new VersionChecks(false, clock.instant()));
        assertThat(notifications).isEqualTo(2);
    }

    @Test
    void startingWhileRunningIsANoOp() {
        service.startCheck();
        service.startCheck();

        assertThat(notifications).isEqualTo(1);
        assertThat(coordinators.tasks).hasSize(1);
    }

    @Test
    void sameContainerIdOnTwoSourcesHasIndependentVersions() {
        var mike = new ContainerKey("mike", "a");
        resolves(A, old);
        resolves(mike, new VersionInfo("9", null, false));
        service.onSnapshot(up("local", "a"));
        service.onSnapshot(up("mike", "a"));
        workers.runAll();
        assertThat(service.cachedVersion(A)).isEqualTo(old);
        assertThat(service.cachedVersion(mike).currentVersion()).isEqualTo("9");
    }

    @Test
    void failedCheckKeepsPreviousResultAndOthersProgress() {
        var b = new ContainerKey("local", "b");
        resolves(A, old);
        service.onSnapshot(up("local", "a"));
        workers.runAll();
        clock.advance(INTERVAL);
        when(resolver.resolve(eq(A), nullable(VersionObservation.class)))
                .thenThrow(new IllegalStateException("registry unavailable"));
        resolves(b, fresh);
        service.onSnapshot(up("local", "a", "b"));
        var scan = service.rescan("local", false);
        workers.runAll();
        assertThat(scan).isCompleted();
        assertThat(service.cachedVersion(A)).isEqualTo(old);
        assertThat(service.cachedVersion(b)).isEqualTo(fresh);
    }

    @Test
    void failedFirstCheckIsRetriedOnlyAfterInterval() {
        when(resolver.resolve(eq(A), nullable(VersionObservation.class)))
                .thenThrow(new IllegalStateException("outage"))
                .thenReturn(observation(old));
        service.onSnapshot(up("local", "a"));
        workers.runAll();
        clock.advance(Duration.ofMinutes(59));
        service.rescan("local", false);
        assertThat(workers.tasks).isEmpty();
        clock.advance(Duration.ofMinutes(1));
        service.rescan("local", false);
        workers.runAll();
        assertThat(service.cachedVersion(A)).isEqualTo(old);
    }

    @Test
    void disappearingContainerDoesNotStopBatch() {
        var b = new ContainerKey("local", "b");
        when(resolver.resolve(eq(A), nullable(VersionObservation.class)))
                .thenThrow(new ContainerNotFoundException("a"));
        resolves(b, old);
        service.onSnapshot(up("local", "a", "b"));
        var scan = service.rescan("local", true);
        workers.runAll();
        assertThat(scan).isCompleted();
        assertThat(service.cachedVersion(A)).isNull();
        assertThat(service.cachedVersion(b)).isEqualTo(old);
    }

    @Test
    void prunedQueuedWorkCannotRestoreRemovedResult() {
        resolves(A, old);
        service.onSnapshot(up("local", "a"));
        service.onSnapshot(up("local"));
        workers.runAll();
        assertThat(service.cachedVersion(A)).isNull();
    }

    @Test
    void closeStopsResourcesAndPreventsNewChecks() {
        service.close();
        service.onSnapshot(up("local", "a"));
        assertThat(service.cachedVersion(A)).isNull();
        assertThat(service.rescan("local", true)).isCompleted();
        assertThat(workers.isShutdown()).isTrue();
        assertThat(coordinators.isShutdown()).isTrue();
        assertThat(workers.tasks).isEmpty();
        verify(scheduler).shutdownNow();
        service.start();
        verifyNoMoreInteractions(scheduler);
    }

    @Test
    void rejectedWorkIsRetriedAfterInterval() {
        workers.shutdown();
        service.onSnapshot(up("local", "a"));
        assertThat(service.cachedVersion(A)).isNull();
        assertThat(service.rescan("local", false)).isCompleted();
    }

    @Test
    void productionWorkersBoundConcurrencyAndReadsDoNotWait() throws Exception {
        var running = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var entered = new java.util.concurrent.atomic.AtomicInteger();
        when(resolver.resolve(any(ContainerKey.class), nullable(VersionObservation.class)))
                .thenAnswer(call -> {
                    entered.incrementAndGet();
                    running.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker did not release");
                    return observation(old);
                });
        try (var actual = new VersionTracker(
                DockerTestSources.catalog("local"), resolver, new VersionProperties(INTERVAL), List.of())) {
            actual.onSnapshot(up("local", "c0", "c1", "c2", "c3", "c4", "c5", "c6", "c7"));
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(entered.get()).isEqualTo(4);
            Assertions.assertTimeoutPreemptively(
                    Duration.ofSeconds(1),
                    () -> assertThat(actual.cachedVersion(new ContainerKey("local", "c0")))
                            .isNull());
        } finally {
            release.countDown();
        }
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
