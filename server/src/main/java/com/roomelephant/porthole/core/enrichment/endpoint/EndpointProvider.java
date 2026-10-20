package com.roomelephant.porthole.core.enrichment.endpoint;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.Endpoint;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface EndpointProvider permits PublishedPortEndpointProvider, TraefikEndpointProvider {
    List<Endpoint> discover(Container container, EnrichmentContext context);
}
