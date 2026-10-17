package com.roomelephant.porthole.domain.component.enrichment;

import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
import com.roomelephant.porthole.domain.service.VersionRefreshService;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(300)
@NullMarked
public class VersionEnricher implements ContainerEnricher {
    private final VersionRefreshService versions;

    public VersionEnricher(VersionRefreshService versions) {
        this.versions = versions;
    }

    @Override
    public Optional<Container> enrich(Container container, ContainerContext context) {
        container.setVersion(versions.getCachedVersionAndRefresh(
                new com.roomelephant.porthole.domain.model.ContainerKey(container.sourceId(), container.id())));
        return Optional.of(container);
    }
}
