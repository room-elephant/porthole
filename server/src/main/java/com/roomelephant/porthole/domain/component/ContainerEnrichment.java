package com.roomelephant.porthole.domain.component;

import com.roomelephant.porthole.domain.component.enrichment.ContainerEnricher;
import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
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

    public Optional<Container> enrich(Container container, ContainerContext context) {
        Container enriched = container;
        for (ContainerEnricher enricher : enrichers) {
            Optional<Container> result = enricher.enrich(enriched, context);
            if (result.isEmpty()) return Optional.empty();
            enriched = result.get();
        }
        return Optional.of(enriched);
    }
}
