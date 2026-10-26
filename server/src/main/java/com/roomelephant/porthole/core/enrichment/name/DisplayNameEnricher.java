package com.roomelephant.porthole.core.enrichment.name;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
@NullMarked
public class DisplayNameEnricher implements ContainerEnricher {
    @Override
    public Optional<Container> enrich(Container container, EnrichmentContext context) {
        container.setDisplayName(context.displayNames().getOrDefault(container.id(), container.name()));
        return Optional.of(container);
    }
}
