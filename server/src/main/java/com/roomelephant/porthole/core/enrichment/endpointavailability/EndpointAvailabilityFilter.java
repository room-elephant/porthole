package com.roomelephant.porthole.core.enrichment.endpointavailability;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(200)
@NullMarked
public class EndpointAvailabilityFilter implements ContainerEnricher {
    @Override
    public Optional<Container> enrich(Container container, EnrichmentContext context) {
        return context.includeWithoutEndpoints() || !container.urlCandidates().isEmpty()
                ? Optional.of(container)
                : Optional.empty();
    }
}
