package com.roomelephant.porthole;

import com.github.dockerjava.api.DockerClient;
import com.roomelephant.porthole.domain.component.DockerSourceRegistry;
import com.roomelephant.porthole.domain.model.DockerSource;
import java.time.Duration;
import java.util.List;

public final class DockerTestSources {
    private DockerTestSources() {}

    public static DockerSourceRegistry of(DockerClient client) {
        return new DockerSourceRegistry(List.of(
                new DockerSource("local", "Local", null, Duration.ofSeconds(5), Duration.ofSeconds(60), client)));
    }
}
