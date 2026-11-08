package com.roomelephant.porthole.core.enrichment.endpoint;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.Endpoint;
import com.roomelephant.porthole.endpoints.EndpointDiscovery;
import com.roomelephant.porthole.endpoints.EndpointTarget;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class UrlCandidateEnricher implements ContainerEnricher {
    private final EndpointDiscovery endpointDiscovery;

    public UrlCandidateEnricher(EndpointDiscovery endpointDiscovery) {
        this.endpointDiscovery = endpointDiscovery;
    }

    @Override
    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        for (Container container : containers) {
            EndpointTarget target = new EndpointTarget(
                    container.name(), container.publishedPorts(), container.labels(), context.publishedHost());
            container.setUrlCandidates(endpointDiscovery.discover(target).stream()
                    .map(candidate -> new Endpoint(candidate.id(), candidate.source(), candidate.url(), false))
                    .toList());
        }
        return containers;
    }
}
