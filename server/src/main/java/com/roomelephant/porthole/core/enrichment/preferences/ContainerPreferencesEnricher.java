package com.roomelephant.porthole.core.enrichment.preferences;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** The one stage that applies a container's saved preferences, on top of what discovery and defaults resolved. */
@Component
@NullMarked
public class ContainerPreferencesEnricher implements ContainerEnricher {
    private final ContainerPreferencesStore store;
    private final EndpointSelectionResolver resolver = new EndpointSelectionResolver();
    private final CustomIconResolver icons;

    public ContainerPreferencesEnricher(ContainerPreferencesStore store, CustomIconResolver icons) {
        this.store = store;
        this.icons = icons;
    }

    @Override
    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        Map<ContainerPreferencesKey, ContainerPreferences> snapshot = store.snapshot();
        for (Container container : containers) {
            ContainerPreferencesKey key = new ContainerPreferencesKey(container.sourceId(), container.name());
            ContainerPreferences preferences = snapshot.getOrDefault(key, new ContainerPreferences(null));
            container.setPreferences(preferences);
            applyEndpointSelection(container, preferences);
            applyIcon(container, preferences);
        }
        return containers;
    }

    private void applyEndpointSelection(Container container, ContainerPreferences preferences) {
        container.setUrlCandidates(resolver.resolve(container.urlCandidates(), preferences.selectedEndpoint()));
    }

    private void applyIcon(Container container, ContainerPreferences preferences) {
        @Nullable SelectedIcon icon = preferences.icon();
        if (icon != null) {
            container.setIconUrl(icons.resolve(icon));
        }
    }
}
