package com.roomelephant.porthole.core.presentation.icon;

import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
public class IconResolver {

    private static final String DEFAULT_ICON = "docker";

    private final Map<String, String> iconMappings;
    private final IconSettings dashboardProperties;

    public IconResolver(Map<String, String> iconMappings, IconSettings dashboardProperties) {
        this.iconMappings = iconMappings;
        this.dashboardProperties = dashboardProperties;
    }

    public @NonNull String resolveIcon(@NonNull String imageName) {
        String iconName = switch (imageName) {
            case String s when s.isBlank() -> DEFAULT_ICON;
            case String s when iconMappings.containsKey(s) -> iconMappings.get(s);
            default -> sanitizeIconName(imageName);
        };
        return buildDashboardIconsUrl(iconName);
    }

    private @NonNull String sanitizeIconName(@NonNull String name) {
        return name.toLowerCase().replace('_', '-');
    }

    private @NonNull String buildDashboardIconsUrl(String iconName) {
        return dashboardProperties.url() + "/" + iconName + dashboardProperties.extension();
    }
}
