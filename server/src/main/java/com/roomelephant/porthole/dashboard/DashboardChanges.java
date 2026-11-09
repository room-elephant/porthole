package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.entries.EntryListener;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef;
import com.roomelephant.porthole.fleet.SnapshotListener;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import com.roomelephant.porthole.versions.VersionChange;
import com.roomelephant.porthole.versions.VersionListener;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The dashboard's revision. Every fleet, entry and version change bumps it at once; subscribers hear about changes
 * at most once per coalescing window, with the revision current when they are told. Revisions start at the start
 * time in epoch milliseconds, so a restarted server never repeats one a browser already saw.
 */
@NullMarked
@Component
public final class DashboardChanges implements SnapshotListener, EntryListener, VersionListener, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DashboardChanges.class);
    private static final Duration COALESCE = Duration.ofMillis(250);

    private final AtomicLong revision;
    private final ScheduledExecutorService scheduler;
    private final Duration coalesce;
    private final List<LongConsumer> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean pending = new AtomicBoolean();
    // The last snapshot heard from each source, to describe what the next one changed.
    private final Map<String, SourceSnapshot> lastSnapshots = new ConcurrentHashMap<>();

    @Autowired
    public DashboardChanges() {
        this(
                Clock.systemUTC(),
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().daemon().name("dashboard-changes").factory()),
                COALESCE);
    }

    DashboardChanges(Clock clock, ScheduledExecutorService scheduler, Duration coalesce) {
        this.revision = new AtomicLong(clock.millis());
        this.scheduler = scheduler;
        this.coalesce = coalesce;
    }

    public long revision() {
        return revision.get();
    }

    /** Hears about each coalesced change. Returns the action that unsubscribes. */
    public Runnable subscribe(LongConsumer subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    @Override
    public void onSnapshot(SourceSnapshot snapshot) {
        SourceSnapshot previous = lastSnapshots.put(snapshot.source().id(), snapshot);
        changed(() -> SnapshotDiff.describe(previous, snapshot));
    }

    @Override
    public void onEntriesChanged(EntryRef ref, @Nullable EntryRecord saved) {
        changed(() -> saved == null ? "entry " + ref.id() + " deleted" : "entry " + ref.id() + " saved: " + saved);
    }

    @Override
    public void onVersionsChanged(VersionChange change) {
        changed(() -> switch (change) {
            case VersionChange.Result result ->
                "version of " + result.key().sourceId() + "/" + result.key().containerId() + ": " + result.previous()
                        + " → " + result.current();
            case VersionChange.Run run ->
                run.checks().running()
                        ? "version check started"
                        : "version check finished at " + run.checks().lastCompletedAt();
        });
    }

    /** Bumps the revision; at DEBUG, logs the new revision with what caused it. */
    private void changed(Supplier<String> cause) {
        long current = revision.incrementAndGet();
        if (log.isDebugEnabled()) log.debug("Dashboard revision {}: {}", current, cause.get());
        if (!pending.compareAndSet(false, true)) return;
        try {
            scheduler.schedule(this::deliver, coalesce.toNanos(), TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException _) {
            pending.set(false);
        }
    }

    private void deliver() {
        pending.set(false);
        long current = revision.get();
        for (LongConsumer subscriber : subscribers) {
            try {
                subscriber.accept(current);
            } catch (RuntimeException e) {
                log.warn("Dashboard change subscriber failed", e);
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {
        scheduler.shutdownNow();
    }
}
