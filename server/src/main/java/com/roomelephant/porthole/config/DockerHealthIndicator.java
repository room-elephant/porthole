package com.roomelephant.porthole.config;

import com.roomelephant.porthole.domain.component.DockerSourceRegistry;
import com.roomelephant.porthole.domain.model.DockerSource;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.*;
import org.springframework.stereotype.Component;

@Component
public class DockerHealthIndicator implements HealthIndicator, AutoCloseable {
    private final DockerSourceRegistry sources;
    private final Duration timeout;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, Future<Boolean>> probes = new ConcurrentHashMap<>();
    private volatile boolean closed;

    @Autowired
    public DockerHealthIndicator(DockerSourceRegistry sources) {
        this(sources, Duration.ofSeconds(1));
    }

    DockerHealthIndicator(DockerSourceRegistry sources, Duration timeout) {
        this.sources = sources;
        this.timeout = timeout;
    }

    @Override
    public Health health() {
        if (closed) return Health.down().build();
        long deadline = System.nanoTime() + timeout.toNanos();
        var current = new LinkedHashMap<String, Future<Boolean>>();
        for (var source : sources.sources())
            current.put(
                    source.id(),
                    probes.compute(
                            source.id(),
                            (id, previous) -> previous != null && !previous.isDone()
                                    ? previous
                                    : workers.submit(() -> ping(source))));
        var details = new LinkedHashMap<String, Object>();
        boolean anyUp = false;
        for (var entry : current.entrySet()) {
            boolean up = false;
            try {
                up = entry.getValue().get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException | CancellationException e) {
                /* Report only a public connectivity outcome. */
            }
            anyUp |= up;
            details.put(
                    entry.getKey(),
                    up
                            ? Map.of("status", "UP")
                            : Map.of(
                                    "status",
                                    "DOWN",
                                    "error",
                                    "Docker is not reachable or the health probe timed out"));
        }
        return (anyUp ? Health.up() : Health.down())
                .withDetail("sources", details)
                .build();
    }

    private boolean ping(DockerSource source) {
        try {
            source.client().pingCmd().exec();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @PreDestroy
    @Override
    public void close() {
        closed = true;
        probes.values().forEach(f -> f.cancel(true));
        workers.shutdownNow();
        probes.clear();
    }
}
