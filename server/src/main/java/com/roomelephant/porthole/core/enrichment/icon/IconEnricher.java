package com.roomelephant.porthole.core.enrichment.icon;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.ImageUtils;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class IconEnricher implements ContainerEnricher {
    private final IconResolver icons;

    public IconEnricher(IconResolver icons) {
        this.icons = icons;
    }

    @Override
    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        for (Container container : containers) {
            container.setIconUrl(icons.resolveIcon(ImageUtils.extractName(container.image())));
        }
        return containers;
    }
}
