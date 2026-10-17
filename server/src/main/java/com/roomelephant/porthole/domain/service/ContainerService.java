package com.roomelephant.porthole.domain.service;

import com.roomelephant.porthole.domain.component.*;
import com.roomelephant.porthole.domain.mapper.ContainerMapper;
import com.roomelephant.porthole.domain.model.*;
import com.roomelephant.porthole.domain.util.DockerFailures;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@NullMarked
public class ContainerService implements AutoCloseable {
    private final DockerSourceRegistry sources;
    private final ContainerMapper mapper;
    private final ContainerEnrichment enrichment;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("inventory-deadlines").factory());
    private final Set<CompletableFuture<Result>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public ContainerService(DockerSourceRegistry sources, ContainerMapper mapper, ContainerEnrichment enrichment) {
        this.sources = sources;
        this.mapper = mapper;
        this.enrichment = enrichment;
    }

    public ContainerInventory getContainers(
            boolean includeWithoutEndpoints, boolean includeStopped, String requestHostname) {
        if (closed) throw new IllegalStateException("Container inventory is closed");
        var futures = sources.sources().stream()
                .map(s -> submit(
                        s,
                        new ContainerContext(
                                s.id(), requestHostname, s.publishedHost(), includeWithoutEndpoints, includeStopped)))
                .toList();
        var results = futures.stream().map(CompletableFuture::join).toList();
        return new ContainerInventory(
                results.stream().map(Result::source).toList(),
                results.stream().flatMap(r -> r.containers().stream()).toList());
    }

    private CompletableFuture<Result> submit(DockerSource source, ContainerContext context) {
        var result = new CompletableFuture<Result>();
        pending.add(result);
        try {
            var task = workers.submit(() -> result.complete(fetch(source, context)));
            var deadline = deadlines.schedule(
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

    private Result fetch(DockerSource source, ContainerContext context) {
        try {
            var raw = source.client().listContainersCmd().withShowAll(true).exec();
            var containers = mapper.toModels(raw, source.id()).stream()
                    .map(c -> enrichment.enrich(c, context))
                    .flatMap(Optional::stream)
                    .toList();
            return new Result(new SourceState(source.id(), source.name(), SourceStatus.UP, null), containers);
        } catch (RuntimeException e) {
            log.warn("Could not list containers from Docker source {}", source.id(), e);
            return DockerFailures.isTransportFailure(e)
                    ? failure(source, SourceStatus.DOWN, "Docker is not reachable")
                    : failure(source, SourceStatus.ERROR, "Could not retrieve containers");
        }
    }

    private Result failure(DockerSource source, SourceStatus status, String message) {
        return new Result(new SourceState(source.id(), source.name(), status, message), List.of());
    }

    @PreDestroy
    @Override
    public void close() {
        closed = true;
        pending.forEach(f -> f.completeExceptionally(new IllegalStateException("Container inventory is closed")));
        deadlines.shutdownNow();
        workers.shutdownNow();
    }

    private record Result(SourceState source, List<Container> containers) {}
}
