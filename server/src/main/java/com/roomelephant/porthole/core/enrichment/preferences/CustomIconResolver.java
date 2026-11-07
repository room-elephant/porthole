package com.roomelephant.porthole.core.enrichment.preferences;

import com.roomelephant.porthole.core.enrichment.icon.IconSettings;
import com.roomelephant.porthole.core.model.SelectedIcon;
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

    public String resolve(SelectedIcon icon) {
        return icon.selectsUpload()
                ? UPLOADED_ICON_PATH + icon.name()
                : settings.url() + "/" + icon.name() + settings.extension();
    }
}
