package com.roomelephant.porthole.domain.component.enrichment;

import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(200)
@NullMarked
public class EndpointAvailabilityFilter implements ContainerEnricher {
    @Override
    public Optional<Container> enrich(Container container, ContainerContext context) {
        return context.includeWithoutEndpoints() || !container.urlCandidates().isEmpty()
                ? Optional.of(container)
                : Optional.empty();
    }
}
