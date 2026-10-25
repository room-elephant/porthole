package com.roomelephant.porthole.core.enrichment.icon;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.ImageUtils;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(250)
@NullMarked
public class IconEnricher implements ContainerEnricher {
    private final IconResolver icons;

    public IconEnricher(IconResolver icons) {
        this.icons = icons;
    }

    @Override
    public Optional<Container> enrich(Container container, EnrichmentContext context) {
        container.setIconUrl(icons.resolveIcon(ImageUtils.extractName(container.image())));
        return Optional.of(container);
    }
}
