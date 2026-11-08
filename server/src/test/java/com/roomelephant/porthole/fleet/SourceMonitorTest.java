package com.roomelephant.porthole.fleet;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SourceMonitorTest {
    private static final Source SOURCE = new Source("walker", "Walker", null, true, Duration.ofMillis(100));
    private static final ContainerSnapshot APP =
            new ContainerSnapshot("id", "app", "nginx:1", "sha256:a", Set.of(), Map.of(), "running", "Up");
    private static final Duration HOUR = Duration.ofHours(1);

    private final FakeDocker docker = new FakeDocker();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final List<SourceSnapshot> changes = new CopyOnWriteArrayList<>();
    private SourceMonitor monitor;

    @AfterEach
    void tearDown() {
        if (monitor != null) monitor.close();
        scheduler.shutdownNow();
        workers.shutdownNow();
    }

    private static FleetSettings settings(Duration interval, Duration nudge) {
        return new FleetSettings(interval, HOUR, nudge, Duration.ofMillis(20), Duration.ofMillis(200), HOUR);
    }

    private SourceMonitor start(FleetSettings settings, Clock clock, Source source) {
        monitor = new SourceMonitor(source, docker, settings, clock, scheduler, workers, changes::add);
        monitor.start();
        return monitor;
    }

    private SourceMonitor start(FleetSettings settings, Clock clock) {
        return start(settings, clock, SOURCE);
    }

    private SourceMonitor start(FleetSettings settings) {
        return start(settings, Clock.systemUTC());
    }

    @Test
    void firstPollPublishesUpSnapshot() {
        docker.lister = _ -> List.of(APP);
        start(settings(HOUR, Duration.ofMillis(10)));
        await().atMost(Duration.ofSeconds(2)).until(() -> monitor.current().status() == SourceSnapshot.Status.UP);
        assertThat(monitor.current().containers()).containsExactly(APP);
        assertThat(changes)
                .singleElement()
                .satisfies(s -> assertThat(s.containers()).containsExactly(APP));
    }

    @Test
    void currentIsInitialBeforeFirstPoll() {
        monitor =
                new SourceMonitor(SOURCE, docker, settings(HOUR, HOUR), Clock.systemUTC(), scheduler, workers, _ -> {});
        assertThat(monitor.current()).isEqualTo(SourceSnapshot.initial(SOURCE));
    }

    @Test
    void pollsAgainAfterInterval() {
        start(settings(Duration.ofMillis(50), Duration.ofMillis(10)));
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.listCalls.get() >= 3);
    }

    @Test
    void hungListTimesOutAndNextPollRuns() {
        CountDownLatch interrupted = new CountDownLatch(1);
        docker.lister = _ -> {
            if (docker.listCalls.get() == 1) {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
            }
            return List.of(APP);
        };
        start(settings(Duration.ofMillis(50), Duration.ofMillis(10)));
        await().atMost(Duration.ofSeconds(2))
                .until(() -> changes.stream().anyMatch(s -> s.status() == SourceSnapshot.Status.UP));
        assertThat(changes.getFirst().status()).isEqualTo(SourceSnapshot.Status.DOWN);
        assertThat(changes.getFirst().error()).isEqualTo("Docker request timed out");
        await().atMost(Duration.ofSeconds(1)).until(() -> interrupted.getCount() == 0);
    }

    @Test
    void unreachableIsDownAndOtherFailureIsError() {
        docker.lister = _ -> {
            throw new DockerUnavailableException(new RuntimeException("refused"));
        };
        start(settings(Duration.ofMillis(20), Duration.ofMillis(10)));
        await().atMost(Duration.ofSeconds(1))
                .until(() -> "Docker is not reachable".equals(monitor.current().error()));
        assertThat(monitor.current().status()).isEqualTo(SourceSnapshot.Status.DOWN);
        docker.lister = _ -> {
            throw new IllegalStateException("bad response");
        };
        await().atMost(Duration.ofSeconds(1)).until(() -> monitor.current().status() == SourceSnapshot.Status.ERROR);
        assertThat(monitor.current().error()).isEqualTo("Could not retrieve containers");
    }

    @Test
    void retainedContainersSurviveFailureUntilRetentionExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-08T10:00:00Z"));
        docker.lister = _ -> List.of(APP);
        start(settings(Duration.ofMillis(20), Duration.ofMillis(10)), clock);
        await().atMost(Duration.ofSeconds(1)).until(() -> monitor.current().status() == SourceSnapshot.Status.UP);
        docker.lister = _ -> {
            throw new DockerUnavailableException(new RuntimeException("refused"));
        };
        await().atMost(Duration.ofSeconds(1)).until(() -> monitor.current().status() == SourceSnapshot.Status.DOWN);
        assertThat(monitor.current().containers()).containsExactly(APP);
        clock.advance(HOUR.plusSeconds(1));
        await().atMost(Duration.ofSeconds(1))
                .until(() -> monitor.current().containers().isEmpty());
        assertThat(monitor.current().lastSuccessAt()).isEqualTo(Instant.parse("2026-11-08T10:00:00Z"));
    }

    @Test
    void burstOfEventsCoalescesIntoOnePoll() {
        start(settings(HOUR, Duration.ofMillis(50)));
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.listCalls.get() == 1 && docker.listener != null);
        for (int i = 0; i < 200; i++) docker.listener.onEvent();
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.listCalls.get() == 2);
        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(1)).until(() -> docker.listCalls.get() == 2);
    }

    @Test
    void nudgeDuringPollRunsOneMorePoll() {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        docker.lister = _ -> {
            if (docker.listCalls.get() == 1) {
                entered.countDown();
                try {
                    release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return List.of();
        };
        start(
                new FleetSettings(HOUR, HOUR, Duration.ofMillis(10), HOUR, HOUR, HOUR),
                Clock.systemUTC(),
                new Source("walker", "Walker", null, true, Duration.ofSeconds(5)));
        await().atMost(Duration.ofSeconds(1)).until(() -> entered.getCount() == 0);
        monitor.nudge();
        monitor.nudge();
        monitor.nudge();
        release.countDown();
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.listCalls.get() == 2);
        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(1)).until(() -> docker.listCalls.get() == 2);
    }

    @Test
    void eventStreamFailureKeepsPollingAndBacksOff() {
        docker.watcher = (_, _) -> {
            throw new DockerUnavailableException(new RuntimeException("events blocked"));
        };
        start(settings(Duration.ofMillis(30), Duration.ofMillis(10)));
        await().atMost(Duration.ofSeconds(3)).until(() -> docker.watchAttempts.size() >= 7);
        List<Instant> attempts = List.copyOf(docker.watchAttempts);
        assertThat(Duration.between(attempts.get(5), attempts.get(6)))
                .as("backoff is capped at the maximum")
                .isLessThan(Duration.ofMillis(350));
        assertThat(Duration.between(attempts.get(0), attempts.get(1))).isGreaterThanOrEqualTo(Duration.ofMillis(15));
        assertThat(Duration.between(attempts.get(1), attempts.get(2))).isGreaterThanOrEqualTo(Duration.ofMillis(35));
        assertThat(Duration.between(attempts.get(2), attempts.get(3))).isGreaterThanOrEqualTo(Duration.ofMillis(75));
        assertThat(docker.listCalls.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void closedStreamResubscribesAndIgnoresStaleListeners() {
        start(settings(HOUR, Duration.ofMillis(10)));
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.listener != null && docker.listCalls.get() == 1);
        ContainerEventListener first = docker.listener;
        first.onClosed(null);
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.watchAttempts.size() == 2);
        first.onEvent();
        first.onClosed(new IllegalStateException("late"));
        await().during(Duration.ofMillis(150))
                .atMost(Duration.ofSeconds(1))
                .until(() -> docker.listCalls.get() == 1 && docker.watchAttempts.size() == 2);
    }

    @Test
    void onChangeOnlyWhenContentChanges() {
        docker.lister = _ -> List.of(APP);
        start(settings(Duration.ofMillis(20), Duration.ofMillis(10)));
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.listCalls.get() >= 4);
        assertThat(changes).hasSize(1);
    }

    @Test
    void closeStopsPollingAndSubscription() throws Exception {
        AutoCloseable subscription = mock(AutoCloseable.class);
        docker.watcher = (_, _) -> subscription;
        start(settings(Duration.ofMillis(20), Duration.ofMillis(10)));
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.listCalls.get() >= 1 && docker.listener != null);
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            monitor.close();
            verify(subscription).close();
        });
        int calls = docker.listCalls.get();
        await().during(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(1))
                .until(() -> docker.listCalls.get() == calls);
        monitor.nudge();
        monitor.start();
        assertThat(docker.listCalls.get()).isEqualTo(calls);
    }

    @Test
    void longLivedSubscriptionResetsBackoff() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-08T10:00:00Z"));
        docker.watcher = (_, _) -> {
            if (docker.watchAttempts.size() <= 3) throw new DockerUnavailableException(new RuntimeException("no"));
            return () -> {};
        };
        start(
                new FleetSettings(
                        HOUR, HOUR, Duration.ofMillis(10), Duration.ofMillis(20), HOUR, Duration.ofSeconds(1)),
                clock);
        await().atMost(Duration.ofSeconds(2)).until(() -> docker.watchAttempts.size() == 4);
        clock.advance(Duration.ofSeconds(2));
        Instant closedAt = Instant.now();
        docker.listener.onClosed(null);
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.watchAttempts.size() == 5);
        assertThat(Duration.between(closedAt, docker.watchAttempts.get(4)))
                .as("a stable stream restarts at the minimum backoff, not 160 ms")
                .isLessThan(Duration.ofMillis(120));
    }

    @Test
    void nudgeBeforeStartDoesNotPoll() {
        monitor = new SourceMonitor(
                SOURCE,
                docker,
                settings(HOUR, Duration.ofMillis(1)),
                Clock.systemUTC(),
                scheduler,
                workers,
                changes::add);
        monitor.nudge();
        await().during(Duration.ofMillis(100)).atMost(Duration.ofSeconds(1)).until(() -> docker.listCalls.get() == 0);
    }

    @Test
    void failingSubscriptionCloseDoesNotFailMonitorClose() {
        docker.watcher = (_, _) -> () -> {
            throw new java.io.IOException("already gone");
        };
        start(settings(HOUR, Duration.ofMillis(10)));
        await().atMost(Duration.ofSeconds(1)).until(() -> docker.listener != null);
        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThatCode(monitor::close).doesNotThrowAnyException());
    }
}
