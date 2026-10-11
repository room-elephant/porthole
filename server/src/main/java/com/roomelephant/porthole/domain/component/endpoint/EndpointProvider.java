package com.roomelephant.porthole.domain.component.endpoint;

import com.roomelephant.porthole.domain.model.ContainerSnapshot;
import com.roomelephant.porthole.domain.model.EndpointContext;
import com.roomelephant.porthole.domain.model.EndpointDTO;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface EndpointProvider permits PublishedPortEndpointProvider, TraefikEndpointProvider {
    List<EndpointDTO> discover(ContainerSnapshot container, EndpointContext context);
}
