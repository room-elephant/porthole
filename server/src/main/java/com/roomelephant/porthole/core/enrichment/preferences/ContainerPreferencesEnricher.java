package com.roomelephant.porthole.core.enrichment.preferences;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ContainerPreferencesEnricher implements ContainerEnricher {
    private final ContainerPreferencesStore store;
    private final EndpointSelectionResolver resolver = new EndpointSelectionResolver();

    public ContainerPreferencesEnricher(ContainerPreferencesStore store) {
        this.store = store;
    }

    @Override
    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        Map<ContainerPreferencesKey, ContainerPreferences> snapshot = store.snapshot();
        for (Container container : containers) {
            ContainerPreferencesKey key = new ContainerPreferencesKey(container.sourceId(), container.name());
            ContainerPreferences preferences = snapshot.getOrDefault(key, new ContainerPreferences(null));
            container.setPreferences(preferences);
            container.setUrlCandidates(resolver.resolve(container.urlCandidates(), preferences.selectedEndpoint()));
        }
        return containers;
    }
}
