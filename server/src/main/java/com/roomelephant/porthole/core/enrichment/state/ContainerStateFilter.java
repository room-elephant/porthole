package com.roomelephant.porthole.core.enrichment.state;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@NullMarked
public class ContainerStateFilter implements ContainerEnricher {
    @Override
    public Optional<Container> enrich(Container container, EnrichmentContext context) {
        boolean stopped = switch (container.state()) {
            case "created", "exited", "dead", "removing" -> true;
            case null, default -> false;
        };
        return context.includeStopped() || !stopped ? Optional.of(container) : Optional.empty();
    }
}
