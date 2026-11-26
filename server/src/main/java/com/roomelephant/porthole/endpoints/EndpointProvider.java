package com.roomelephant.porthole.endpoints;

import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface EndpointProvider permits PublishedPortEndpointProvider, TraefikEndpointProvider {
    List<Candidate> discover(EndpointTarget target);
}
