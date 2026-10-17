package com.roomelephant.porthole.domain.component.endpoint;

import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
import com.roomelephant.porthole.domain.model.Endpoint;
import com.roomelephant.porthole.domain.util.EndpointUrls;
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
    public List<Endpoint> discover(Container container, ContainerContext context) {
        return container.publishedPorts().stream()
                .sorted()
                .map(port -> new Endpoint(
                        "published-port:" + port,
                        "published-port",
                        EndpointUrls.publishedPortUrl(hostResolver.resolve(context), port)))
                .toList();
    }
}
