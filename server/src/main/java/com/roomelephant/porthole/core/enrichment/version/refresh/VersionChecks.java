package com.roomelephant.porthole.core.enrichment.version.refresh;

import java.time.Instant;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The state of manual version checks: whether one is running, and when the last one finished (null if none has
 * since startup).
 */
@NullMarked
public record VersionChecks(boolean running, @Nullable Instant lastCompletedAt) {}
