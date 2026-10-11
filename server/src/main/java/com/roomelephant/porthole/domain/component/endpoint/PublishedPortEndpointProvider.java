package com.roomelephant.porthole.domain.component.endpoint;

import com.roomelephant.porthole.domain.model.ContainerSnapshot;
import com.roomelephant.porthole.domain.model.EndpointContext;
import com.roomelephant.porthole.domain.model.EndpointDTO;
import com.roomelephant.porthole.domain.util.EndpointUrls;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public final class PublishedPortEndpointProvider implements EndpointProvider {
    @Override
    public List<EndpointDTO> discover(ContainerSnapshot container, EndpointContext context) {
        return container.publishedPorts().stream()
                .sorted()
                .map(port -> new EndpointDTO(
                        "published-port:" + port,
                        "published-port",
                        EndpointUrls.publishedPortUrl(context.publishedHost(), port)))
                .toList();
    }
}
