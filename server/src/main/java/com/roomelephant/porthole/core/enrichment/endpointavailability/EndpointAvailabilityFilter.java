package com.roomelephant.porthole.core.enrichment.endpointavailability;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class EndpointAvailabilityFilter implements ContainerEnricher {
    @Override
    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        return context.includeWithoutEndpoints()
                ? containers
                : containers.stream().filter(c -> !c.urlCandidates().isEmpty()).toList();
    }
}
