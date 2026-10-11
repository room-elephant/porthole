package com.roomelephant.porthole.domain.component;

import com.roomelephant.porthole.domain.component.endpoint.EndpointDiscovery;
import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.EndpointContext;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerEnrichment {
    private final EndpointDiscovery endpointDiscovery;

    public ContainerEnrichment(EndpointDiscovery endpointDiscovery) {
        this.endpointDiscovery = endpointDiscovery;
    }

    public Container enrich(Container container, EndpointContext context) {
        return container.withUrlCandidates(endpointDiscovery.discover(container, context));
    }
}
