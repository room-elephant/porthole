package com.roomelephant.porthole.core.enrichment;

import com.roomelephant.porthole.core.model.Container;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ContainerEnricher {
    /** Processes one source's current batch, preserving encounter order without structurally modifying the input. */
    List<Container> enrich(List<Container> containers, EnrichmentContext context);
}
