package com.roomelephant.porthole.fleet;

import java.time.Duration;
import org.jspecify.annotations.NullMarked;

/** Timing for source monitoring. */
@NullMarked
public record FleetSettings(
        Duration pollInterval,
        Duration snapshotRetention,
        Duration nudgeDelay,
        Duration minBackoff,
        Duration maxBackoff,
        Duration stableSubscription) {
    public FleetSettings(Duration pollInterval, Duration snapshotRetention) {
        this(
                pollInterval,
                snapshotRetention,
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30));
    }
}
