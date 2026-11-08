package com.roomelephant.porthole.core.enrichment.version;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshService;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.fleet.ContainerKey;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class VersionEnricher implements ContainerEnricher {
    private final VersionRefreshService versions;

    public VersionEnricher(VersionRefreshService versions) {
        this.versions = versions;
    }

    @Override
    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        for (Container container : containers) {
            container.setVersion(versions.cachedVersion(new ContainerKey(container.sourceId(), container.id())));
        }
        return containers;
    }
}
