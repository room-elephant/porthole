package com.roomelephant.porthole.domain.component.enrichment;

import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ContainerEnricher {
    Optional<Container> enrich(Container container, ContainerContext context);
}
