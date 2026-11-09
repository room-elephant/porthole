package com.roomelephant.porthole.endpoints;

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

    public List<Candidate> discover(EndpointTarget target) {
        LinkedHashMap<String, Candidate> endpoints = new LinkedHashMap<>();
        for (EndpointProvider provider : providers) {
            for (Candidate endpoint : provider.discover(target)) {
                endpoints.putIfAbsent(endpoint.id(), endpoint);
            }
        }
        return endpoints.values().stream()
                .sorted(Comparator.comparing(Candidate::source).thenComparing(Candidate::url))
                .toList();
    }
}
