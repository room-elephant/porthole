package com.roomelephant.porthole.core.enrichment;

import com.roomelephant.porthole.core.model.Container;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerEnrichment {
    private final List<ContainerEnricher> enrichers;

    public ContainerEnrichment(List<ContainerEnricher> enrichers) {
        this.enrichers = List.copyOf(enrichers);
    }

    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        List<Container> enriched = containers;
        for (ContainerEnricher enricher : enrichers) {
            if (enriched.isEmpty()) break;
            enriched = enricher.enrich(enriched, context);
        }
        return enriched;
    }
}
