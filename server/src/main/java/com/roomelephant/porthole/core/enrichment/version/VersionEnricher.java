package com.roomelephant.porthole.core.enrichment.version;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshService;
import com.roomelephant.porthole.core.model.Container;
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
    public Optional<Container> enrich(Container container, EnrichmentContext context) {
        container.setVersion(versions.getCachedVersionAndRefresh(
                new com.roomelephant.porthole.core.model.ContainerKey(container.sourceId(), container.id())));
        return Optional.of(container);
    }
}
