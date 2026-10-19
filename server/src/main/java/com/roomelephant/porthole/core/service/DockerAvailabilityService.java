package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.inbound.CheckDockerAvailability;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@NullMarked
public class DockerAvailabilityService implements CheckDockerAvailability, AutoCloseable {
    private final DockerOperations sources;
    private final Duration timeout;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, Future<Boolean>> probes = new ConcurrentHashMap<>();
    private volatile boolean closed;

    @Autowired
    public DockerAvailabilityService(DockerOperations sources) {
        this(sources, Duration.ofSeconds(1));
    }

    DockerAvailabilityService(DockerOperations sources, Duration timeout) {
        this.sources = sources;
        this.timeout = timeout;
    }

    @Override
    public DockerAvailability checkAvailability() {
        if (closed) return new DockerAvailability(Map.of());
        long deadline = System.nanoTime() + timeout.toNanos();
        return awaitProbes(startOrReuseProbes(), deadline);
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
        Map<String, Future<Boolean>> current = new LinkedHashMap<>();
        for (DockerSource source : sources.sources())
            current.put(
                    source.id(),
                    probes.compute(
                            source.id(),
                            (id, previous) -> previous != null && !previous.isDone()
                                    ? previous
                                    : workers.submit(() -> ping(source))));
        return current;
    }

    private DockerAvailability awaitProbes(Map<String, Future<Boolean>> current, long deadline) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        for (Map.Entry<String, Future<Boolean>> entry : current.entrySet()) {
            results.put(entry.getKey(), awaitProbe(entry.getValue(), deadline));
        }
        return new DockerAvailability(results);
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

    private boolean ping(DockerSource source) {
        try {
            sources.ping(source.id());
            return true;
        } catch (RuntimeException _) {
            return false;
        }
    }
}
