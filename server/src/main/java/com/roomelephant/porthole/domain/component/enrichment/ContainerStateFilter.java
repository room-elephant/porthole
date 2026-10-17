package com.roomelephant.porthole.domain.component.enrichment;

import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@NullMarked
public class ContainerStateFilter implements ContainerEnricher {
    @Override
    public Optional<Container> enrich(Container container, ContainerContext context) {
        boolean stopped = switch (container.state()) {
            case "created", "exited", "dead", "removing" -> true;
            case null, default -> false;
        };
        return context.includeStopped() || !stopped ? Optional.of(container) : Optional.empty();
    }
}
