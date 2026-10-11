package com.roomelephant.porthole.domain.component.endpoint;

import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
import com.roomelephant.porthole.domain.model.Endpoint;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class EndpointDiscovery {
    private final List<EndpointProvider> providers;

    public EndpointDiscovery(List<EndpointProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public List<Endpoint> discover(Container container, ContainerContext context) {
        LinkedHashMap<String, Endpoint> endpoints = new LinkedHashMap<String, Endpoint>();
        for (EndpointProvider provider : providers) {
            for (Endpoint endpoint : provider.discover(container, context)) {
                endpoints.putIfAbsent(endpoint.id(), endpoint);
            }
        }
        return endpoints.values().stream()
                .sorted(Comparator.comparing(Endpoint::source).thenComparing(Endpoint::url))
                .toList();
    }
}
