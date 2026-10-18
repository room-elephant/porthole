package com.roomelephant.porthole.inbound.health;

import com.roomelephant.porthole.core.model.DockerSource;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.*;
import org.springframework.stereotype.Component;

@Component
public class DockerHealthIndicator implements HealthIndicator, AutoCloseable {
    private static final Map<String, String> UP_DETAILS = Map.of("status", "UP");
    private static final Map<String, String> DOWN_DETAILS =
            Map.of("status", "DOWN", "error", "Docker is not reachable or the health probe timed out");

    private final DockerOperations sources;
    private final Duration timeout;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, Future<Boolean>> probes = new ConcurrentHashMap<>();
    private volatile boolean closed;

    @Autowired
    public DockerHealthIndicator(DockerOperations sources) {
        this(sources, Duration.ofSeconds(1));
    }

    DockerHealthIndicator(DockerOperations sources, Duration timeout) {
        this.sources = sources;
        this.timeout = timeout;
    }

    @Override
    public Health health() {
        if (closed) return Health.down().build();
        long deadline = System.nanoTime() + timeout.toNanos();
        return buildHealth(startOrReuseProbes(), deadline);
    }

    @PreDestroy
    @Override
    public void close() {
        closed = true;
        probes.values().forEach(f -> f.cancel(true));
        workers.shutdownNow();
        probes.clear();
    }

    private Map<String, Future<Boolean>> startOrReuseProbes() {
        var current = new LinkedHashMap<String, Future<Boolean>>();
        for (var source : sources.sources())
            current.put(
                    source.id(),
                    probes.compute(
                            source.id(),
                            (id, previous) -> previous != null && !previous.isDone()
                                    ? previous
                                    : workers.submit(() -> ping(source))));
        return current;
    }

    private Health buildHealth(Map<String, Future<Boolean>> current, long deadline) {
        var details = new LinkedHashMap<String, Object>();
        boolean anyUp = false;
        for (var entry : current.entrySet()) {
            boolean up = awaitProbe(entry.getValue(), deadline);
            anyUp |= up;
            details.put(entry.getKey(), sourceDetails(up));
        }
        return (anyUp ? Health.up() : Health.down())
                .withDetail("sources", details)
                .build();
    }

    private boolean awaitProbe(Future<Boolean> probe, long deadline) {
        try {
            return probe.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | CancellationException _) {
            /* Report only a public connectivity outcome. */
        }
        return false;
    }

    private Map<String, String> sourceDetails(boolean up) {
        return up ? UP_DETAILS : DOWN_DETAILS;
    }

    private boolean ping(DockerSource source) {
        try {
            sources.ping(source.id());
            return true;
        } catch (RuntimeException _) {
            return false;
        }
    }
}
