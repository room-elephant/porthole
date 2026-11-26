package com.roomelephant.porthole.fleet;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Every configured source's latest snapshot, kept current in the background. Reads never touch Docker. */
@Component
@NullMarked
public final class Fleet implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(Fleet.class);

    private final List<SnapshotListener> listeners;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("fleet-monitor").factory());
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, SourceMonitor> monitors = new LinkedHashMap<>();

    @Autowired
    public Fleet(
            SourceCatalog catalog, DockerOperations docker, FleetSettings settings, List<SnapshotListener> listeners) {
        this(catalog, docker, settings, listeners, Clock.systemUTC());
    }

    Fleet(
            SourceCatalog catalog,
            DockerOperations docker,
            FleetSettings settings,
            List<SnapshotListener> listeners,
            Clock clock) {
        this.listeners = List.copyOf(listeners);
        for (Source source : catalog.sources()) {
            monitors.put(
                    source.id(), new SourceMonitor(source, docker, settings, clock, scheduler, workers, this::publish));
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        monitors.values().forEach(SourceMonitor::start);
    }

    /** Every source's snapshot, in configuration order. */
    public List<SourceSnapshot> snapshots() {
        return monitors.values().stream().map(SourceMonitor::current).toList();
    }

    public SourceSnapshot snapshot(String sourceId) {
        SourceMonitor monitor = monitors.get(sourceId);
        if (monitor == null) throw new IllegalArgumentException("Unknown sourceId: " + sourceId);
        return monitor.current();
    }

    @PreDestroy
    @Override
    public void close() {
        monitors.values().forEach(SourceMonitor::close);
        scheduler.shutdownNow();
        workers.shutdownNow();
    }

    private void publish(SourceSnapshot snapshot) {
        for (SnapshotListener listener : listeners) {
            try {
                listener.onSnapshot(snapshot);
            } catch (RuntimeException e) {
                log.warn(
                        "Snapshot listener failed for Docker source {}",
                        snapshot.source().id(),
                        e);
            }
        }
    }
}
