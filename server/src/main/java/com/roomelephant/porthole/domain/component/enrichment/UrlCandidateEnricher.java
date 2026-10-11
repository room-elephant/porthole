package com.roomelephant.porthole.domain.component.enrichment;

import com.roomelephant.porthole.domain.component.endpoint.EndpointDiscovery;
import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.EndpointContext;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@NullMarked
public class UrlCandidateEnricher implements ContainerEnricher {
    private final EndpointDiscovery endpointDiscovery;

    public UrlCandidateEnricher(EndpointDiscovery endpointDiscovery) {
        this.endpointDiscovery = endpointDiscovery;
    }

    @Override
    public Container enrich(Container container, EndpointContext context) {
        return container.withUrlCandidates(endpointDiscovery.discover(container, context));
    }
}
