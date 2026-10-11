package com.roomelephant.porthole.domain.component.enrichment;

import com.roomelephant.porthole.domain.component.endpoint.EndpointDiscovery;
import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
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
    public Optional<Container> enrich(Container container, ContainerContext context) {
        return Optional.of(container.withUrlCandidates(endpointDiscovery.discover(container, context)));
    }
}
