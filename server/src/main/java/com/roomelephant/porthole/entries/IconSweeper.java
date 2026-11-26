package com.roomelephant.porthole.entries;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Removes uploaded icons no entry uses, at startup and then on an interval. */
@NullMarked
@Component
public class IconSweeper implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(IconSweeper.class);

    private final EntryService entries;
    private final IconService icons;
    private final EntriesProperties properties;
    private final Clock clock;
    private @Nullable ScheduledExecutorService scheduler;
    private boolean closed;

    @Autowired
    public IconSweeper(EntryService entries, IconService icons, EntriesProperties properties) {
        this(entries, icons, properties, Clock.systemUTC());
    }

    IconSweeper(EntryService entries, IconService icons, EntriesProperties properties, Clock clock) {
        this.entries = entries;
        this.icons = icons;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (scheduler != null || closed) return;
        ScheduledExecutorService started = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("icon-sweep").factory());
        long interval = properties.iconSweepInterval().toNanos();
        started.scheduleWithFixedDelay(this::sweep, 0, interval, TimeUnit.NANOSECONDS);
        scheduler = started;
    }

    void sweep() {
        try {
            int deleted = icons.sweep(entries.state().uploadIds(), clock.instant(), properties.unreferencedIconAge());
            if (deleted > 0) log.info("Removed {} unused uploaded icons", deleted);
        } catch (RuntimeException e) {
            log.warn("Could not remove unused uploaded icons", e);
        }
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        closed = true;
        if (scheduler != null) scheduler.shutdownNow();
    }
}
