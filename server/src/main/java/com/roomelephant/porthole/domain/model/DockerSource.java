package com.roomelephant.porthole.domain.model;

import com.github.dockerjava.api.DockerClient;
import java.time.Duration;
import org.jspecify.annotations.*;

@NullMarked
public record DockerSource(
        String id,
        String name,
        @Nullable String publishedHost,
        Duration connectionTimeout,
        Duration responseTimeout,
        DockerClient client) {}
