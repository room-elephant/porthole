package com.roomelephant.porthole.core.enrichment;

import com.roomelephant.porthole.core.model.Container;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ContainerEnricher {
    Optional<Container> enrich(Container container, EnrichmentContext context);
}
