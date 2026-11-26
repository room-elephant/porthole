package com.roomelephant.porthole.dashboard.web;

import com.roomelephant.porthole.dashboard.DashboardProperties;
import com.roomelephant.porthole.dashboard.IconSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** The only place icon URLs are built: the library CDN, uploaded icons, and Porthole's own logo. */
@NullMarked
@Component
public class IconUrls {
    static final String UPLOADED_ICON_PATH = "/api/icons/";
    static final String SELF_ICON = "porthole-mark.svg";

    private final DashboardProperties.Icons icons;

    public IconUrls(DashboardProperties properties) {
        this.icons = properties.icons();
    }

    public @Nullable String url(IconSource source) {
        return switch (source) {
            case IconSource.Library library -> icons.url() + "/" + library.name() + icons.extension();
            case IconSource.Upload upload -> UPLOADED_ICON_PATH + upload.id();
            case IconSource.Self _ -> SELF_ICON;
            case IconSource.None _ -> null;
        };
    }
}
