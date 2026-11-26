package com.roomelephant.porthole.fleet.docker;

import com.github.dockerjava.api.DockerClient;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record DockerSourceRegistry(List<DockerConnection> sources) implements AutoCloseable {
    public DockerSourceRegistry {
        if (sources.isEmpty()
                || sources.stream().map(DockerConnection::id).distinct().count() != sources.size())
            throw new IllegalArgumentException("Docker sources must be nonempty and have unique IDs");
        sources = List.copyOf(sources);
    }

    public DockerConnection require(String id) {
        return sources.stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Docker source: " + id));
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (DockerConnection source : sources) {
            List<DockerClient> clients = source.events() == source.client()
                    ? List.of(source.client())
                    : List.of(source.client(), source.events());
            for (DockerClient client : clients)
                try {
                    client.close();
                } catch (IOException e) {
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
        }
        if (failure != null) throw failure;
    }
}
