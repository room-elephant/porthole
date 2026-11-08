package com.roomelephant.porthole.core.enrichment.preferences;

import com.roomelephant.porthole.core.enrichment.icon.IconSettings;
import com.roomelephant.porthole.entries.Icon;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/** Turns a user's saved icon choice into the URL the inventory reports. */
@Component
@NullMarked
public class CustomIconResolver {
    private static final String UPLOADED_ICON_PATH = "/api/icons/";

    private final IconSettings settings;

    public CustomIconResolver(IconSettings settings) {
        this.settings = settings;
    }

    public String resolve(Icon icon) {
        return switch (icon) {
            case Icon.Upload upload -> UPLOADED_ICON_PATH + upload.id();
            case Icon.Library library -> settings.url() + "/" + library.name() + settings.extension();
        };
    }
}
