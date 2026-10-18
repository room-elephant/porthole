package com.roomelephant.porthole.core.enrichment.endpoint;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@NullMarked
public class UrlCandidateEnricher implements ContainerEnricher {
    private final EndpointDiscovery endpointDiscovery;

    public UrlCandidateEnricher(EndpointDiscovery endpointDiscovery) {
        this.endpointDiscovery = endpointDiscovery;
    }

    @Override
    public Optional<Container> enrich(Container container, EnrichmentContext context) {
        container.setUrlCandidates(endpointDiscovery.discover(container, context));
        return Optional.of(container);
    }
}
