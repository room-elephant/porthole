package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.shared.ImageReference;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Picks a Dashboard Icons library name for an image: a configured mapping, else the image's own name. */
@NullMarked
@Component
public class DefaultIcons {
    static final String DOCKER = "docker";

    private final Map<String, String> iconMappings;

    public DefaultIcons(Map<String, String> iconMappings) {
        this.iconMappings = Map.copyOf(iconMappings);
    }

    public String nameFor(@Nullable String image) {
        String leaf =
                ImageReference.tryParse(image).map(ImageReference::leafName).orElse("");
        if (leaf.isBlank()) return DOCKER;
        String mapped = iconMappings.get(leaf);
        return mapped != null ? mapped : leaf.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
