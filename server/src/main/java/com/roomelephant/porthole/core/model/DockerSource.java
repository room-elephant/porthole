package com.roomelephant.porthole.core.model;

import java.time.Duration;
import org.jspecify.annotations.*;

@NullMarked
public record DockerSource(
        String id, String name, @Nullable String publishedHost, Duration connectionTimeout, Duration responseTimeout) {}
