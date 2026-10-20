package com.roomelephant.porthole.core.enrichment.endpoint;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.Endpoint;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public final class PublishedPortEndpointProvider implements EndpointProvider {
    private final PublishedHostResolver hostResolver;

    public PublishedPortEndpointProvider(PublishedHostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    @Override
    public List<Endpoint> discover(Container container, EnrichmentContext context) {
        return container.publishedPorts().stream()
                .sorted()
                .map(port -> new Endpoint(
                        "published-port:" + port,
                        "published-port",
                        EndpointUrls.publishedPortUrl(hostResolver.resolve(context), port)))
                .toList();
    }
}
