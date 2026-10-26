package com.roomelephant.porthole.core.enrichment.state;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerStateFilter implements ContainerEnricher {
    @Override
    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        return context.includeStopped()
                ? containers
                : containers.stream().filter(this::isActive).toList();
    }

    private boolean isActive(Container container) {
        return switch (container.state()) {
            case "created", "exited", "dead", "removing" -> false;
            case null, default -> true;
        };
    }
}
