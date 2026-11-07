package com.roomelephant.porthole.core.enrichment.icon;

import com.roomelephant.porthole.core.enrichment.ContainerEnricher;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.ImageUtils;
import com.roomelephant.porthole.core.model.SelectedIcon;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class IconEnricher implements ContainerEnricher {
    public static final String UPLOADED_ICON_PATH = "/api/icons/";

    private final IconResolver icons;

    public IconEnricher(IconResolver icons) {
        this.icons = icons;
    }

    @Override
    public List<Container> enrich(List<Container> containers, EnrichmentContext context) {
        for (Container container : containers) {
            container.setIconUrl(iconUrl(container));
        }
        return containers;
    }

    private String iconUrl(Container container) {
        @Nullable SelectedIcon selected = container.preferences().icon();
        if (selected == null) {
            return icons.resolveIcon(ImageUtils.extractName(container.image()));
        }
        return selected.selectsUpload() ? UPLOADED_ICON_PATH + selected.name() : icons.libraryUrl(selected.name());
    }
}
