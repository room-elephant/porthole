package com.roomelephant.porthole.core.enrichment.version.refresh;

import com.roomelephant.porthole.core.enrichment.version.resolution.VersionObservation;
import com.roomelephant.porthole.core.enrichment.version.resolution.VersionResolver;
import com.roomelephant.porthole.core.model.ContainerKey;
import com.roomelephant.porthole.core.model.ContainerSnapshot;
import com.roomelephant.porthole.core.model.DockerSource;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.core.model.exception.NotFoundException;
import com.roomelephant.porthole.core.port.inbound.RefreshVersions;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Owns background resolution; no Docker or registry work occurs in cache readers. */
@Service
@Slf4j
@NullMarked
public class VersionRefreshService implements RefreshVersions, AutoCloseable {
    private final DockerOperations sources;
    private final VersionResolver versionResolver;
    private final VersionRefreshSettings properties;
    private final Clock clock;
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService coordinators;
    private final Map<ContainerKey, Entry> entries = new HashMap<>();
    private final Set<String> forcedScans = new HashSet<>();
    private long revision;
    private boolean started;
    private boolean closed;
    private final Map<String, CompletableFuture<Void>> currentScans = new HashMap<>();

    @Autowired
    public VersionRefreshService(
            DockerOperations sources, VersionResolver versionResolver, VersionRefreshSettings properties) {
        this(
                sources,
                versionResolver,
                properties,
                Clock.systemUTC(),
                Executors.newFixedThreadPool(
                        4, Thread.ofVirtual().name("version-check-", 0).factory()),
                Executors.newScheduledThreadPool(
                        sources.sources().size(),
                        Thread.ofPlatform().daemon().name("version-refresh").factory()),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    VersionRefreshService(
            DockerOperations sources,
            VersionResolver versionResolver,
            VersionRefreshSettings properties,
            Clock clock,
            ExecutorService workers,
            ScheduledExecutorService scheduler,
            ExecutorService coordinators) {
        this.sources = sources;
        this.versionResolver = versionResolver;
        this.properties = properties;
        this.clock = clock;
        this.workers = workers;
        this.scheduler = scheduler;
        this.coordinators = coordinators;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (started || closed) return;
        started = true;
        for (DockerSource source : sources.sources())
            scheduler.scheduleWithFixedDelay(
                    () -> refreshSource(source.id()).join(),
                    0,
                    properties.refreshInterval().toNanos(),
                    TimeUnit.NANOSECONDS);
    }

    public synchronized @Nullable VersionInfo getCachedVersionAndRefresh(ContainerKey containerId) {
        if (closed) return null;
        Entry entry = entries.computeIfAbsent(containerId, _ -> new Entry());
        entry.observedRevision = ++revision;
        if (entry.value == null) submitIfDue(containerId, entry);
        return entry.value;
    }

    /** Queues an immediate scan without doing Docker or registry I/O on the HTTP thread. */
    public synchronized void requestRefresh() {
        if (closed) throw new RejectedExecutionException("Version refresh service is closed");
        List<String> pending = sources.sources().stream()
                .map(DockerSource::id)
                .filter(id -> !forcedScans.contains(id))
                .toList();
        if (pending.isEmpty()) return;
        forcedScans.addAll(pending);
        try {
            CompletableFuture<Void> invalidation =
                    CompletableFuture.runAsync(versionResolver::invalidateRegistryCache, coordinators);
            for (String id : pending) {
                invalidation
                        .thenComposeAsync(_ -> refreshSource(id, true), coordinators)
                        .whenComplete((_, failure) -> {
                            if (failure != null)
                                log.warn("Could not force version refresh for source {}: {}", id, failure.getMessage());
                            completeForcedScan(id);
                        });
            }
        } catch (RejectedExecutionException e) {
            forcedScans.removeAll(pending);
            throw e;
        }
    }

    private synchronized void completeForcedScan(String sourceId) {
        forcedScans.remove(sourceId);
    }

    /** Called on a coordinator, never by HTTP handlers. Completion includes all checks in this scan. */
    CompletableFuture<Void> refreshSource(String sourceId) {
        return refreshSource(sourceId, false);
    }

    private CompletableFuture<Void> refreshSource(String sourceId, boolean force) {
        CompletableFuture<Void> scan;
        long scanRevision;
        synchronized (this) {
            if (closed) return CompletableFuture.completedFuture(null);
            if (!force && currentScans.containsKey(sourceId)) return currentScans.get(sourceId);
            scan = new CompletableFuture<>();
            if (!force) currentScans.put(sourceId, scan);
            scanRevision = ++revision;
        }
        try {
            List<ContainerSnapshot> containers = sources.listContainers(sourceId);
            List<CompletableFuture<Void>> checks = new ArrayList<>();
            synchronized (this) {
                if (!closed) {
                    Set<ContainerKey> present = new HashSet<>();
                    for (ContainerSnapshot container : containers) {
                        String dockerId = container.id();
                        if (dockerId == null) continue;
                        ContainerKey id = new ContainerKey(sourceId, dockerId);
                        present.add(id);
                        Entry entry = entries.computeIfAbsent(id, _ -> new Entry());
                        // A concurrent older inventory must not prune a newer scan's discoveries.
                        entry.observedRevision = Math.max(entry.observedRevision, scanRevision);
                        checks.add(submitIfDue(id, entry, force));
                    }
                    entries.entrySet()
                            .removeIf(item -> item.getKey().sourceId().equals(sourceId)
                                    && !present.contains(item.getKey())
                                    && item.getValue().observedRevision < scanRevision);
                }
            }
            CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                    .whenComplete((_, _) -> completeScan(sourceId, scan));
        } catch (Exception e) {
            log.warn("Could not refresh container version inventory: {}", e.getMessage());
            completeScan(sourceId, scan);
        }
        return scan;
    }

    private synchronized void completeScan(String sourceId, CompletableFuture<Void> scan) {
        currentScans.remove(sourceId, scan);
        scan.complete(null);
    }

    // Callers hold this monitor only for state transitions and task submission, never remote I/O.
    private CompletableFuture<Void> submitIfDue(ContainerKey id, Entry entry) {
        return submitIfDue(id, entry, false);
    }

    private CompletableFuture<Void> submitIfDue(ContainerKey id, Entry entry, boolean force) {
        if (entry.inFlight != null) return entry.inFlight;
        if (!force && entry.retryAt != null && clock.instant().isBefore(entry.retryAt))
            return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> check = new CompletableFuture<>();
        entry.inFlight = check;
        try {
            workers.execute(() -> resolve(id, entry, check));
        } catch (RejectedExecutionException _) {
            entry.inFlight = null;
            entry.retryAt = clock.instant().plus(properties.refreshInterval());
            check.complete(null);
        }
        return check;
    }

    private void resolve(ContainerKey id, Entry entry, CompletableFuture<Void> check) {
        try {
            VersionObservation previous;
            synchronized (this) {
                if (closed || entries.get(id) != entry) return;
                previous = entry.observation;
            }
            VersionObservation result = versionResolver.resolve(id, previous);
            VersionInfo value = result.toVersionInfo();
            synchronized (this) {
                if (!closed && entries.get(id) == entry) {
                    entry.observation = result;
                    entry.value = value;
                }
            }
        } catch (NotFoundException _) {
            synchronized (this) {
                entries.remove(id, entry);
            }
        } catch (Exception e) {
            log.warn("Could not refresh version for container {}: {}", id, e.getMessage());
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
        for (CompletableFuture<Void> scan : currentScans.values()) scan.complete(null);
        currentScans.clear();
        forcedScans.clear();
    }

    private static final class Entry {
        private @Nullable VersionObservation observation;
        private @Nullable VersionInfo value;
        private @Nullable Instant retryAt;
        private @Nullable CompletableFuture<Void> inFlight;
        private long observedRevision;
    }
}
