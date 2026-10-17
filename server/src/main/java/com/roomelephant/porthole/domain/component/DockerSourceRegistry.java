package com.roomelephant.porthole.domain.component;

import com.roomelephant.porthole.domain.model.DockerSource;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record DockerSourceRegistry(List<DockerSource> sources) implements AutoCloseable {
    public DockerSourceRegistry {
        if (sources.isEmpty()
                || sources.stream().map(DockerSource::id).distinct().count() != sources.size())
            throw new IllegalArgumentException("Docker sources must be nonempty and have unique IDs");
        sources = List.copyOf(sources);
    }

    public DockerSource require(String id) {
        return sources.stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Docker source: " + id));
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (var source : sources)
            try {
                source.client().close();
            } catch (IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        if (failure != null) throw failure;
    }
}
