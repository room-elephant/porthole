package com.roomelephant.porthole.domain.component;

import com.roomelephant.porthole.domain.component.enrichment.ContainerEnricher;
import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.EndpointContext;
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

    public Container enrich(Container container, EndpointContext context) {
        var enriched = container;
        for (var enricher : enrichers) {
            enriched = enricher.enrich(enriched, context);
        }
        return enriched;
    }
}
