package com.roomelephant.porthole.domain.service;

import com.github.dockerjava.api.DockerClient;
import com.roomelephant.porthole.config.properties.VersionProperties;
import com.roomelephant.porthole.domain.model.VersionInfo;
import com.roomelephant.porthole.domain.model.exception.NotFoundException;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
public class VersionRefreshService implements AutoCloseable {
    private final DockerClient dockerClient;
    private final VersionService versionService;
    private final VersionProperties properties;
    private final Clock clock;
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Entry> entries = new HashMap<>();
    private long revision;
    private boolean started;
    private boolean closed;
    private @Nullable CompletableFuture<Void> currentScan;

    @Autowired
    public VersionRefreshService(
            DockerClient dockerClient, VersionService versionService, VersionProperties properties) {
        this(
                dockerClient,
                versionService,
                properties,
                Clock.systemUTC(),
                Executors.newFixedThreadPool(
                        4, Thread.ofVirtual().name("version-check-", 0).factory()),
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().daemon().name("version-refresh").factory()));
    }

    VersionRefreshService(
            DockerClient dockerClient,
            VersionService versionService,
            VersionProperties properties,
            Clock clock,
            ExecutorService workers,
            ScheduledExecutorService scheduler) {
        this.dockerClient = dockerClient;
        this.versionService = versionService;
        this.properties = properties;
        this.clock = clock;
        this.workers = workers;
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (started || closed) return;
        started = true;
        scheduler.scheduleWithFixedDelay(
                () -> refreshAll().join(), 0, properties.refreshInterval().toNanos(), TimeUnit.NANOSECONDS);
    }

    public synchronized @Nullable VersionInfo getCachedVersionAndRefresh(String containerId) {
        if (closed) return null;
        Entry entry = entries.computeIfAbsent(containerId, _ -> new Entry());
        entry.observedRevision = ++revision;
        if (entry.value == null) submitIfDue(containerId, entry);
        return entry.value;
    }

    /** Called on the coordinator, never by HTTP handlers. Completion includes all checks in this scan. */
    CompletableFuture<Void> refreshAll() {
        CompletableFuture<Void> scan;
        long scanRevision;
        synchronized (this) {
            if (closed) return CompletableFuture.completedFuture(null);
            if (currentScan != null) return currentScan;
            scan = new CompletableFuture<>();
            currentScan = scan;
            scanRevision = ++revision;
        }
        try {
            var containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            var checks = new ArrayList<CompletableFuture<Void>>();
            synchronized (this) {
                if (!closed) {
                    var present = new HashSet<String>();
                    for (var container : containers) {
                        String id = container.getId();
                        if (id == null) continue;
                        present.add(id);
                        Entry entry = entries.computeIfAbsent(id, _ -> new Entry());
                        checks.add(submitIfDue(id, entry));
                    }
                    entries.entrySet()
                            .removeIf(item -> !present.contains(item.getKey())
                                    && item.getValue().observedRevision < scanRevision);
                }
            }
            CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                    .whenComplete((_, _) -> completeScan(scan));
        } catch (Exception e) {
            log.warn("Could not refresh container version inventory: {}", e.getMessage());
            completeScan(scan);
        }
        return scan;
    }

    private synchronized void completeScan(CompletableFuture<Void> scan) {
        if (currentScan == scan) currentScan = null;
        scan.complete(null);
    }

    // Callers hold this monitor only for state transitions and task submission, never remote I/O.
    private CompletableFuture<Void> submitIfDue(String id, Entry entry) {
        if (entry.inFlight != null) return entry.inFlight;
        if (entry.retryAt != null && clock.instant().isBefore(entry.retryAt))
            return CompletableFuture.completedFuture(null);
        var check = new CompletableFuture<Void>();
        entry.inFlight = check;
        try {
            workers.execute(() -> resolve(id, entry, check));
        } catch (RejectedExecutionException e) {
            entry.inFlight = null;
            entry.retryAt = clock.instant().plus(properties.refreshInterval());
            check.complete(null);
        }
        return check;
    }

    private void resolve(String id, Entry entry, CompletableFuture<Void> check) {
        try {
            synchronized (this) {
                if (closed || entries.get(id) != entry) return;
            }
            VersionInfo result = versionService.getVersionInfo(id);
            synchronized (this) {
                if (!closed && entries.get(id) == entry) entry.value = result;
            }
        } catch (NotFoundException e) {
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
        workers.shutdownNow();
        for (Entry entry : entries.values()) {
            if (entry.inFlight != null) entry.inFlight.complete(null);
        }
        entries.clear();
        if (currentScan != null) currentScan.complete(null);
    }

    private static final class Entry {
        private @Nullable VersionInfo value;
        private @Nullable Instant retryAt;
        private @Nullable CompletableFuture<Void> inFlight;
        private long observedRevision;
    }
}
