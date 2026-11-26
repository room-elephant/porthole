package com.roomelephant.porthole.dashboard.web;

import com.roomelephant.porthole.dashboard.DashboardChanges;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open {@code GET /api/events} streams. Each starts with the current revision, then hears every coalesced change
 * and a keepalive comment every 15 seconds. At most 100 are open at once. Streams close when the application stops,
 * before the web server waits for in-flight requests.
 */
@NullMarked
@Component
public final class EventStreams implements SmartLifecycle, AutoCloseable {
    static final int LIMIT = 100;
    static final Duration HEARTBEAT = Duration.ofSeconds(15);
    static final long RETRY_MILLIS = 5_000;

    private final DashboardChanges changes;
    private final ScheduledExecutorService scheduler;
    private final int limit;
    private final Duration heartbeat;
    private final Supplier<SseEmitter> emitters;
    private final Set<SseEmitter> open = ConcurrentHashMap.newKeySet();
    private boolean running;
    private @Nullable Runnable unsubscribe;
    private @Nullable ScheduledFuture<?> keepalive;

    @Autowired
    public EventStreams(DashboardChanges changes) {
        this(
                changes,
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().daemon().name("event-streams").factory()),
                LIMIT,
                HEARTBEAT,
                () -> new SseEmitter(0L));
    }

    EventStreams(
            DashboardChanges changes,
            ScheduledExecutorService scheduler,
            int limit,
            Duration heartbeat,
            Supplier<SseEmitter> emitters) {
        this.changes = changes;
        this.scheduler = scheduler;
        this.limit = limit;
        this.heartbeat = heartbeat;
        this.emitters = emitters;
    }

    /** A new stream that has been sent the current revision, or empty when the limit is reached or stopping. */
    public Optional<SseEmitter> open() {
        SseEmitter emitter;
        synchronized (this) {
            if (!running || open.size() >= limit) return Optional.empty();
            emitter = emitters.get();
            open.add(emitter);
        }
        emitter.onCompletion(() -> open.remove(emitter));
        emitter.onTimeout(() -> open.remove(emitter));
        emitter.onError(_ -> open.remove(emitter));
        send(emitter, changed(changes.revision()).reconnectTime(RETRY_MILLIS));
        return Optional.of(emitter);
    }

    int openCount() {
        return open.size();
    }

    private static SseEmitter.SseEventBuilder changed(long revision) {
        return SseEmitter.event().name("changed").data("{\"revision\":" + revision + "}");
    }

    // Builders are stateful, so each stream gets its own.
    private void broadcast(Supplier<SseEmitter.SseEventBuilder> event) {
        for (SseEmitter emitter : open) send(emitter, event.get());
    }

    // A failed send means the client is gone; the container reports the error to Spring on its own.
    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException _) {
            open.remove(emitter);
        }
    }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        unsubscribe = changes.subscribe(revision -> broadcast(() -> changed(revision)));
        long millis = heartbeat.toMillis();
        keepalive = scheduler.scheduleWithFixedDelay(
                () -> broadcast(() -> SseEmitter.event().comment("keepalive")), millis, millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        List<SseEmitter> closing;
        synchronized (this) {
            if (!running) return;
            running = false;
            if (unsubscribe != null) unsubscribe.run();
            if (keepalive != null) keepalive.cancel(false);
            closing = List.copyOf(open);
            open.clear();
        }
        closing.forEach(SseEmitter::complete);
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    @Override
    @PreDestroy
    public void close() {
        stop();
        scheduler.shutdownNow();
    }
}
