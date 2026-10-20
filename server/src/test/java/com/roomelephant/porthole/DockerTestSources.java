package com.roomelephant.porthole;

import com.github.dockerjava.api.DockerClient;
import java.time.Duration;
import java.util.List;

public final class DockerTestSources {
    private DockerTestSources() {}

    public static com.roomelephant.porthole.core.port.outbound.DockerOperations operationsOf(DockerClient client) {
        return new com.roomelephant.porthole.outbound.docker.DockerJavaOperations(
                new com.roomelephant.porthole.outbound.docker.DockerSourceRegistry(
                        List.of(new com.roomelephant.porthole.outbound.docker.DockerConnection(
                                "local", "Local", null, Duration.ofSeconds(5), Duration.ofSeconds(60), true, client))));
    }
}
