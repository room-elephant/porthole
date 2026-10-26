package com.roomelephant.porthole.core.enrichment;

import com.roomelephant.porthole.core.enrichment.name.DisplayNameResolver;
import com.roomelephant.porthole.core.model.Container;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.*;

@NullMarked
public record EnrichmentContext(
        String sourceId,
        @Nullable String publishedHost,
        boolean includeWithoutEndpoints,
        boolean includeStopped,
        Map<String, String> displayNames) {
    public EnrichmentContext {
        displayNames = Map.copyOf(displayNames);
    }

    public static EnrichmentContext forContainers(
            String sourceId,
            @Nullable String publishedHost,
            boolean includeWithoutEndpoints,
            boolean includeStopped,
            List<Container> containers) {
        List<String> resolved = new DisplayNameResolver().resolve(containers);
        Map<String, String> names = new HashMap<>();
        for (int i = 0; i < containers.size(); i++) {
            names.put(containers.get(i).id(), resolved.get(i));
        }
        return new EnrichmentContext(sourceId, publishedHost, includeWithoutEndpoints, includeStopped, names);
    }
}
