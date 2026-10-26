package com.roomelephant.porthole.core.enrichment.name;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class DisplayNameEnricher implements ContainerEnricher {
    private final DisplayNameResolver names = new DisplayNameResolver();

    @Override
    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        List<String> displayNames = names.resolve(containers);
        for (int i = 0; i < containers.size(); i++) {
            containers.get(i).setDisplayName(displayNames.get(i));
        }
        return containers;
    }
}
