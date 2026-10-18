package com.roomelephant.porthole.core.enrichment;

import com.roomelephant.porthole.core.model.Container;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerEnrichment {
    private final List<ContainerEnricher> enrichers;

    public ContainerEnrichment(List<ContainerEnricher> enrichers) {
        this.enrichers = List.copyOf(enrichers);
    }

    public Optional<Container> enrich(Container container, EnrichmentContext context) {
        Container enriched = container;
        for (ContainerEnricher enricher : enrichers) {
            Optional<Container> result = enricher.enrich(enriched, context);
            if (result.isEmpty()) return Optional.empty();
            enriched = result.get();
        }
        return Optional.of(enriched);
    }
}
