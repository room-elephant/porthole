package com.roomelephant.porthole.fleet.docker;

import com.github.dockerjava.api.DockerClient;
import org.jspecify.annotations.NullMarked;

/**
 * The clients for one configured source. {@code client} bounds every request by the source's response timeout;
 * {@code events} has none, because the container event stream stays open and can be quiet for hours.
 */
@NullMarked
public record DockerConnection(String id, DockerClient client, DockerClient events) {
    public DockerConnection(String id, DockerClient client) {
        this(id, client, client);
    }
}
