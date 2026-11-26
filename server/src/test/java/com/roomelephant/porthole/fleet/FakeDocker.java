package com.roomelephant.porthole.fleet;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** A scriptable Docker port for monitor tests. */
final class FakeDocker implements DockerOperations {
    volatile Function<String, List<ContainerSnapshot>> lister = _ -> List.of();
    volatile BiFunction<String, ContainerEventListener, AutoCloseable> watcher = (_, _) -> () -> {};
    final AtomicInteger listCalls = new AtomicInteger();
    final List<Instant> watchAttempts = new CopyOnWriteArrayList<>();
    volatile @Nullable ContainerEventListener listener;

    @Override
    public List<ContainerSnapshot> listContainers(String sourceId) {
        listCalls.incrementAndGet();
        return lister.apply(sourceId);
    }

    @Override
    public ContainerInspection inspectContainer(ContainerKey key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable List<String> getRepoDigests(String sourceId, @Nullable String imageId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AutoCloseable watchContainerEvents(String sourceId, ContainerEventListener listener) {
        watchAttempts.add(Instant.now());
        this.listener = listener;
        return watcher.apply(sourceId, listener);
    }
}
