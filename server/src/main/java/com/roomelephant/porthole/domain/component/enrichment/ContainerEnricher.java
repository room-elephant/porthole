package com.roomelephant.porthole.domain.component.enrichment;

import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.EndpointContext;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ContainerEnricher {
    Container enrich(Container container, EndpointContext context);
}
