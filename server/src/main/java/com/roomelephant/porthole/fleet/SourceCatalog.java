package com.roomelephant.porthole.fleet;

import java.util.List;
import org.jspecify.annotations.NullMarked;

/** The configured Docker sources, in configuration order. */
@NullMarked
public final class SourceCatalog {
    private final List<Source> sources;

    public SourceCatalog(List<Source> sources) {
        if (sources.isEmpty() || sources.stream().map(Source::id).distinct().count() != sources.size()) {
            throw new IllegalArgumentException("Docker sources must be nonempty and have unique IDs");
        }
        this.sources = List.copyOf(sources);
    }

    public List<Source> sources() {
        return sources;
    }

    public Source require(String id) {
        return sources.stream()
                .filter(source -> source.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown sourceId: " + id));
    }

    public boolean contains(String id) {
        return sources.stream().anyMatch(source -> source.id().equals(id));
    }
}
