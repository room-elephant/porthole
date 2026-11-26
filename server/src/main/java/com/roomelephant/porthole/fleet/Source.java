package com.roomelephant.porthole.fleet;

import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A configured Docker source. {@code deadline} bounds one container listing. */
@NullMarked
public record Source(String id, String name, @Nullable String publishedHost, boolean local, Duration deadline) {}
