package com.roomelephant.porthole.domain.component.endpoint;

import com.roomelephant.porthole.domain.model.ContainerSnapshot;
import com.roomelephant.porthole.domain.model.EndpointContext;
import com.roomelephant.porthole.domain.model.EndpointDTO;
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

    public List<EndpointDTO> discover(ContainerSnapshot container, EndpointContext context) {
        var endpoints = new LinkedHashMap<String, EndpointDTO>();
        for (var provider : providers) {
            for (var endpoint : provider.discover(container, context)) {
                endpoints.putIfAbsent(endpoint.id(), endpoint);
            }
        }
        return endpoints.values().stream()
                .sorted(Comparator.comparing(EndpointDTO::source).thenComparing(EndpointDTO::url))
                .toList();
    }
}
