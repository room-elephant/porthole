package com.roomelephant.porthole.outbound.docker;

import com.github.dockerjava.api.DockerClient;
import java.time.Duration;
import org.jspecify.annotations.*;

@NullMarked
public record DockerConnection(
        String id,
        String name,
        @Nullable String publishedHost,
        Duration connectionTimeout,
        Duration responseTimeout,
        boolean local,
        DockerClient client) {}
