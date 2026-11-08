package com.roomelephant.porthole;

import com.github.dockerjava.api.DockerClient;
import java.time.Duration;
import java.util.List;

public final class DockerTestSources {
    private DockerTestSources() {}

    public static com.roomelephant.porthole.fleet.SourceCatalog catalog(String... ids) {
        return new com.roomelephant.porthole.fleet.SourceCatalog(java.util.Arrays.stream(ids)
                .map(id -> new com.roomelephant.porthole.fleet.Source(id, id, null, true, Duration.ofSeconds(65)))
                .toList());
    }

    public static com.roomelephant.porthole.fleet.DockerOperations operationsOf(DockerClient client) {
        return new com.roomelephant.porthole.fleet.docker.DockerJavaOperations(
                new com.roomelephant.porthole.fleet.docker.DockerSourceRegistry(
                        List.of(new com.roomelephant.porthole.fleet.docker.DockerConnection("local", client))));
    }
}
