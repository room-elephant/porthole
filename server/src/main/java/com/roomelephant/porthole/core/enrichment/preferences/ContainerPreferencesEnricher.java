package com.roomelephant.porthole.core.enrichment.preferences;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryService;
import com.roomelephant.porthole.entries.EntryState;
import com.roomelephant.porthole.entries.Icon;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** The one stage that applies a container's saved entry, on top of what discovery and defaults resolved. */
@Component
@NullMarked
public class ContainerPreferencesEnricher implements ContainerEnricher {
    private final EntryService entries;
    private final EndpointSelectionResolver resolver = new EndpointSelectionResolver();
    private final CustomIconResolver icons;

    public ContainerPreferencesEnricher(EntryService entries, CustomIconResolver icons) {
        this.entries = entries;
        this.icons = icons;
    }

    @Override
    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        EntryState state = entries.state();
        for (Container container : containers) {
            @Nullable
            EntryRecord entry = state.find(new ContainerRef(container.sourceId(), container.name()))
                    .orElse(null);
            container.setEntry(entry);
            container.setUrlCandidates(
                    resolver.resolve(container.urlCandidates(), entry == null ? null : entry.endpoint()));
            @Nullable Icon icon = entry == null ? null : entry.icon();
            if (icon != null) container.setIconUrl(icons.resolve(icon));
        }
        return containers;
    }
}
