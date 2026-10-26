package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@NullMarked
public class DockerDiscoveryService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DockerDiscoveryService.class);

    private final DockerOperations sources;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("docker-discovery-deadlines").factory());
    private final Set<CompletableFuture<DockerDiscoveryResult>> pending = ConcurrentHashMap.newKeySet();
    private boolean closed;

    public DockerDiscoveryService(DockerOperations sources) {
        this.sources = sources;
    }

    public List<DockerDiscoveryResult> discover() {
        List<CompletableFuture<DockerDiscoveryResult>> futures;
        synchronized (this) {
            if (closed) throw new IllegalStateException("Docker discovery is closed");
            futures = sources.sources().stream().map(this::submit).toList();
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private CompletableFuture<DockerDiscoveryResult> submit(DockerSource source) {
        CompletableFuture<DockerDiscoveryResult> result = new CompletableFuture<>();
        pending.add(result);
        try {
            Future<?> task = workers.submit(() -> result.complete(fetch(source)));
            ScheduledFuture<?> deadline = deadlines.schedule(
                    () -> {
                        if (result.complete(failure(source, SourceStatus.DOWN, "Docker request timed out")))
                            task.cancel(true);
                    },
                    source.connectionTimeout().plus(source.responseTimeout()).toNanos(),
                    TimeUnit.NANOSECONDS);
            result.whenComplete((r, e) -> {
                deadline.cancel(false);
                pending.remove(result);
            });
        } catch (RuntimeException e) {
            pending.remove(result);
            result.completeExceptionally(e);
        }
        return result;
    }

    private DockerDiscoveryResult fetch(DockerSource source) {
        try {
            List<ContainerSnapshot> containers = sources.listContainers(source.id());
            return new DockerDiscoveryResult(
                    source, new SourceState(source.id(), source.name(), SourceStatus.UP, null), containers);
        } catch (RuntimeException e) {
            log.warn("Could not list containers from Docker source {}", source.id(), e);
            return e instanceof com.roomelephant.porthole.core.model.exception.DockerUnavailableException
                    ? failure(source, SourceStatus.DOWN, "Docker is not reachable")
                    : failure(source, SourceStatus.ERROR, "Could not retrieve containers");
        }
    }

    private DockerDiscoveryResult failure(DockerSource source, SourceStatus status, String message) {
        return new DockerDiscoveryResult(
                source, new SourceState(source.id(), source.name(), status, message), List.of());
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        closed = true;
        pending.forEach(f -> f.completeExceptionally(new IllegalStateException("Docker discovery is closed")));
        deadlines.shutdownNow();
        workers.shutdownNow();
    }
}
