package com.roomelephant.porthole.domain.component.endpoint;

import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
import com.roomelephant.porthole.domain.model.Endpoint;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface EndpointProvider permits PublishedPortEndpointProvider, TraefikEndpointProvider {
    List<Endpoint> discover(Container container, ContainerContext context);
}
