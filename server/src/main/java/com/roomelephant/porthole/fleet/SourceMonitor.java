package com.roomelephant.porthole.fleet;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps one source's snapshot current. Polls on an interval, polls early when Docker reports a container event, and
 * never runs two polls at once. The scheduler only hands work to {@code workers}, so a slow Docker call never delays
 * another source. The lock is never held during a Docker call.
 */
@NullMarked
final class SourceMonitor implements AutoCloseable {
    static final String TIMED_OUT = "Docker request timed out";
    static final String UNREACHABLE = "Docker is not reachable";
    static final String FAILED = "Could not retrieve containers";

    private static final Logger log = LoggerFactory.getLogger(SourceMonitor.class);

    private final Source source;
    private final DockerOperations docker;
    private final FleetSettings settings;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workers;
    private final Consumer<SourceSnapshot> onChange;

    private SourceSnapshot current;
    private boolean started;
    private boolean closed;
    private boolean polling;
    private boolean rerun;
    private @Nullable ScheduledFuture<?> nextPoll;
    private @Nullable ScheduledFuture<?> resubscribe;
    private @Nullable AutoCloseable subscription;
    private long generation;
    private Duration backoff;

    SourceMonitor(
            Source source,
            DockerOperations docker,
            FleetSettings settings,
            Clock clock,
            ScheduledExecutorService scheduler,
            ExecutorService workers,
            Consumer<SourceSnapshot> onChange) {
        this.source = source;
        this.docker = docker;
        this.settings = settings;
        this.clock = clock;
        this.scheduler = scheduler;
        this.workers = workers;
        this.onChange = onChange;
        this.current = SourceSnapshot.initial(source);
        this.backoff = settings.minBackoff();
    }

    synchronized void start() {
        if (started || closed) return;
        started = true;
        schedulePoll(Duration.ZERO);
        scheduleSubscribe(Duration.ZERO);
    }

    synchronized SourceSnapshot current() {
        return current;
    }

    /** Polls after the nudge delay, unless a poll is already due sooner. */
    synchronized void nudge() {
        if (closed || !started) return;
        if (polling) {
            rerun = true;
            return;
        }
        ScheduledFuture<?> pending = nextPoll;
        if (pending != null
                && pending.getDelay(TimeUnit.NANOSECONDS)
                        <= settings.nudgeDelay().toNanos()) return;
        schedulePoll(settings.nudgeDelay());
    }

    @Override
    public void close() {
        AutoCloseable open;
        synchronized (this) {
            closed = true;
            cancel(nextPoll);
            cancel(resubscribe);
            open = subscription;
            subscription = null;
            generation++;
        }
        closeQuietly(open);
    }

    // Callers hold the lock.
    private void schedulePoll(Duration delay) {
        cancel(nextPoll);
        nextPoll = schedule(this::poll, delay);
    }

    private @Nullable ScheduledFuture<?> schedule(Runnable task, Duration delay) {
        try {
            return scheduler.schedule(() -> execute(task), delay.toNanos(), TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException _) {
            return null;
        }
    }

    private void execute(Runnable task) {
        try {
            workers.execute(task);
        } catch (RejectedExecutionException _) {
            // Shutting down.
        }
    }

    private void poll() {
        SourceSnapshot previous;
        synchronized (this) {
            if (closed) return;
            if (polling) {
                rerun = true;
                return;
            }
            polling = true;
            nextPoll = null;
            previous = current;
        }
        SourceSnapshot next;
        try {
            next = previous.succeeded(list(), clock.instant());
        } catch (TimeoutException _) {
            next = failure(previous, SourceSnapshot.Status.DOWN, TIMED_OUT, null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            next = cause instanceof DockerUnavailableException
                    ? failure(previous, SourceSnapshot.Status.DOWN, UNREACHABLE, cause)
                    : failure(previous, SourceSnapshot.Status.ERROR, FAILED, cause);
        } catch (InterruptedException | RejectedExecutionException _) {
            // Only shutdown interrupts or rejects a poll.
            synchronized (this) {
                polling = false;
            }
            Thread.currentThread().interrupt();
            return;
        }
        boolean changed;
        synchronized (this) {
            if (closed) return;
            changed = !current.sameContent(next);
            current = next;
            polling = false;
            schedulePoll(rerun ? settings.nudgeDelay() : settings.pollInterval());
            rerun = false;
        }
        if (changed) onChange.accept(next);
    }

    private List<ContainerSnapshot> list() throws TimeoutException, ExecutionException, InterruptedException {
        Future<List<ContainerSnapshot>> listing = workers.submit(() -> docker.listContainers(source.id()));
        try {
            return listing.get(source.deadline().toNanos(), TimeUnit.NANOSECONDS);
        } finally {
            listing.cancel(true);
        }
    }

    private SourceSnapshot failure(
            SourceSnapshot previous, SourceSnapshot.Status status, String message, @Nullable Throwable cause) {
        if (previous.status() != status || !message.equals(previous.error())) {
            log.warn("Docker source {} is {}: {}", source.id(), status, message, cause);
        } else {
            log.debug("Docker source {} is still {}: {}", source.id(), status, message, cause);
        }
        return previous.failed(status, message, clock.instant(), settings.snapshotRetention());
    }

    // Callers hold the lock.
    private void scheduleSubscribe(Duration delay) {
        cancel(resubscribe);
        resubscribe = schedule(this::subscribe, delay);
    }

    private void subscribe() {
        long subscriber;
        synchronized (this) {
            if (closed) return;
            subscriber = ++generation;
            resubscribe = null;
        }
        Instant since = clock.instant();
        try {
            AutoCloseable opened = docker.watchContainerEvents(source.id(), new Listener(subscriber, since));
            synchronized (this) {
                if (!closed && subscriber == generation) {
                    subscription = opened;
                    return;
                }
            }
            closeQuietly(opened);
        } catch (RuntimeException e) {
            log.debug("Could not watch container events on Docker source {}", source.id(), e);
            streamEnded(subscriber, since);
        }
    }

    private synchronized void streamEnded(long subscriber, Instant since) {
        if (closed || subscriber != generation) return;
        generation++;
        subscription = null;
        if (Duration.between(since, clock.instant()).compareTo(settings.stableSubscription()) >= 0) {
            backoff = settings.minBackoff();
        }
        Duration delay = backoff;
        Duration doubled = backoff.multipliedBy(2);
        backoff = doubled.compareTo(settings.maxBackoff()) > 0 ? settings.maxBackoff() : doubled;
        scheduleSubscribe(delay);
    }

    private synchronized boolean isCurrent(long subscriber) {
        return !closed && subscriber == generation;
    }

    private static void cancel(@Nullable ScheduledFuture<?> task) {
        if (task != null) task.cancel(false);
    }

    private void closeQuietly(@Nullable AutoCloseable open) {
        if (open == null) return;
        try {
            open.close();
        } catch (Exception e) {
            log.debug("Could not close container events on Docker source {}", source.id(), e);
        }
    }

    private final class Listener implements ContainerEventListener {
        private final long subscriber;
        private final Instant since;

        private Listener(long subscriber, Instant since) {
            this.subscriber = subscriber;
            this.since = since;
        }

        @Override
        public void onEvent() {
            if (isCurrent(subscriber)) nudge();
        }

        @Override
        public void onClosed(@Nullable Throwable failure) {
            log.debug("Container events ended on Docker source {}", source.id(), failure);
            streamEnded(subscriber, since);
        }
    }
}
