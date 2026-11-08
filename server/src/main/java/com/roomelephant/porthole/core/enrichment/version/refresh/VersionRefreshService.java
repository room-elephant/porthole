package com.roomelephant.porthole.core.enrichment.version.refresh;

import com.roomelephant.porthole.core.enrichment.version.resolution.VersionObservation;
import com.roomelephant.porthole.core.enrichment.version.resolution.VersionResolver;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.core.port.inbound.RefreshVersions;
import com.roomelephant.porthole.fleet.ContainerKey;
import com.roomelephant.porthole.fleet.ContainerNotFoundException;
import com.roomelephant.porthole.fleet.ContainerSnapshot;
import com.roomelephant.porthole.fleet.SnapshotListener;
import com.roomelephant.porthole.fleet.Source;
import com.roomelephant.porthole.fleet.SourceCatalog;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Owns background version checks. Learns about containers from source snapshots, so it never lists containers
 * itself, and cache readers never cause Docker or registry work.
 */
@Service
@NullMarked
public class VersionRefreshService implements RefreshVersions, SnapshotListener, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(VersionRefreshService.class);

    private final SourceCatalog catalog;
    private final VersionResolver versionResolver;
    private final VersionRefreshSettings properties;
    private final Clock clock;
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService coordinators;
    private final Map<ContainerKey, Entry> entries = new HashMap<>();
    private final Map<String, SourceSnapshot> latest = new HashMap<>();
    private final Set<String> forcedScans = new HashSet<>();
    private boolean started;
    private boolean closed;

    @Autowired
    public VersionRefreshService(
            SourceCatalog catalog, VersionResolver versionResolver, VersionRefreshSettings properties) {
        this(
                catalog,
                versionResolver,
                properties,
                Clock.systemUTC(),
                Executors.newFixedThreadPool(
                        4, Thread.ofVirtual().name("version-check-", 0).factory()),
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().daemon().name("version-refresh").factory()),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    VersionRefreshService(
            SourceCatalog catalog,
            VersionResolver versionResolver,
            VersionRefreshSettings properties,
            Clock clock,
            ExecutorService workers,
            ScheduledExecutorService scheduler,
            ExecutorService coordinators) {
        this.catalog = catalog;
        this.versionResolver = versionResolver;
        this.properties = properties;
        this.clock = clock;
        this.workers = workers;
        this.scheduler = scheduler;
        this.coordinators = coordinators;
    }

    /** Re-checks due containers one interval after readiness and after each completed scan. */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (started || closed) return;
        started = true;
        long interval = properties.refreshInterval().toNanos();
        for (Source source : catalog.sources())
            scheduler.scheduleWithFixedDelay(
                    () -> rescan(source.id(), false).join(), interval, interval, TimeUnit.NANOSECONDS);
    }

    public synchronized @Nullable VersionInfo cachedVersion(ContainerKey key) {
        Entry entry = entries.get(key);
        return entry == null ? null : entry.value;
    }

    /** Checks new containers and changed images at once, and forgets containers an available source no longer has. */
    @Override
    public synchronized void onSnapshot(SourceSnapshot snapshot) {
        if (closed) return;
        String sourceId = snapshot.source().id();
        latest.put(sourceId, snapshot);
        if (snapshot.status() != SourceSnapshot.Status.UP) return;
        Set<ContainerKey> present = new HashSet<>();
        for (ContainerSnapshot container : snapshot.containers()) {
            String id = container.id();
            if (id == null) continue;
            ContainerKey key = new ContainerKey(sourceId, id);
            present.add(key);
            Entry entry = entries.get(key);
            if (entry == null || !Objects.equals(entry.imageId, container.imageId())) {
                Entry replacement = new Entry(container.imageId(), entry == null ? null : entry.value);
                entries.put(key, replacement);
                submitIfDue(key, replacement, false);
            }
        }
        entries.keySet().removeIf(key -> key.sourceId().equals(sourceId) && !present.contains(key));
    }

    /** Queues an immediate scan of every source without doing Docker or registry I/O on the caller. */
    @Override
    public synchronized void requestRefresh() {
        if (closed) throw new RejectedExecutionException("Version refresh service is closed");
        List<String> pending = catalog.sources().stream()
                .map(Source::id)
                .filter(id -> !forcedScans.contains(id))
                .toList();
        if (pending.isEmpty()) return;
        forcedScans.addAll(pending);
        try {
            CompletableFuture<Void> invalidation =
                    CompletableFuture.runAsync(versionResolver::invalidateRegistryCache, coordinators);
            for (String id : pending) {
                invalidation
                        .thenComposeAsync(_ -> rescan(id, true), coordinators)
                        .whenComplete((_, failure) -> {
                            if (failure != null)
                                log.warn("Could not force version refresh for source {}: {}", id, failure.getMessage());
                            completeForcedScan(id);
                        });
            }
        } catch (RejectedExecutionException e) {
            pending.forEach(forcedScans::remove);
            throw e;
        }
    }

    private synchronized void completeForcedScan(String sourceId) {
        forcedScans.remove(sourceId);
    }

    /** Checks the containers of the source's latest available snapshot. Completes when those checks finish. */
    synchronized CompletableFuture<@Nullable Void> rescan(String sourceId, boolean force) {
        SourceSnapshot snapshot = latest.get(sourceId);
        if (closed || snapshot == null || snapshot.status() != SourceSnapshot.Status.UP)
            return CompletableFuture.completedFuture(null);
        List<CompletableFuture<@Nullable Void>> checks = new ArrayList<>();
        for (ContainerSnapshot container : snapshot.containers()) {
            if (container.id() == null) continue;
            ContainerKey key = new ContainerKey(sourceId, container.id());
            Entry entry = entries.get(key);
            if (entry != null) checks.add(submitIfDue(key, entry, force));
        }
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new));
    }

    // Callers hold this monitor only for state transitions and task submission, never remote I/O.
    private CompletableFuture<@Nullable Void> submitIfDue(ContainerKey key, Entry entry, boolean force) {
        if (entry.inFlight != null) return entry.inFlight;
        if (!force && entry.retryAt != null && clock.instant().isBefore(entry.retryAt))
            return CompletableFuture.completedFuture(null);
        CompletableFuture<@Nullable Void> check = new CompletableFuture<>();
        entry.inFlight = check;
        try {
            workers.execute(() -> resolve(key, entry, check));
        } catch (RejectedExecutionException _) {
            entry.inFlight = null;
            entry.retryAt = clock.instant().plus(properties.refreshInterval());
            check.complete(null);
        }
        return check;
    }

    private void resolve(ContainerKey key, Entry entry, CompletableFuture<@Nullable Void> check) {
        try {
            VersionObservation previous;
            synchronized (this) {
                if (closed || entries.get(key) != entry) return;
                previous = entry.observation;
            }
            VersionObservation result = versionResolver.resolve(key, previous);
            VersionInfo value = result.toVersionInfo();
            synchronized (this) {
                if (!closed && entries.get(key) == entry) {
                    entry.observation = result;
                    entry.value = value;
                }
            }
        } catch (ContainerNotFoundException _) {
            synchronized (this) {
                entries.remove(key, entry);
            }
        } catch (Exception e) {
            log.warn("Could not refresh version for container {}: {}", key, e.getMessage());
        } finally {
            synchronized (this) {
                entry.inFlight = null;
                entry.retryAt = clock.instant().plus(properties.refreshInterval());
            }
            check.complete(null);
        }
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        if (closed) return;
        closed = true;
        scheduler.shutdownNow();
        coordinators.shutdownNow();
        workers.shutdownNow();
        for (Entry entry : entries.values()) {
            if (entry.inFlight != null) entry.inFlight.complete(null);
        }
        entries.clear();
        latest.clear();
        forcedScans.clear();
    }

    private static final class Entry {
        private final @Nullable String imageId;
        private @Nullable VersionInfo value;
        private @Nullable VersionObservation observation;
        private @Nullable Instant retryAt;
        private @Nullable CompletableFuture<@Nullable Void> inFlight;

        private Entry(@Nullable String imageId, @Nullable VersionInfo value) {
            this.imageId = imageId;
            this.value = value;
        }
    }
}
