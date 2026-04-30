package com.roomelephant.porthole.domain.component;

import com.roomelephant.porthole.config.properties.DashboardProperties;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
public class IconComponent {

    private static final String DEFAULT_ICON = "docker";

    private final Map<String, String> iconMappings;
    private final DashboardProperties dashboardProperties;

    public IconComponent(Map<String, String> iconMappings, DashboardProperties dashboardProperties) {
        this.iconMappings = iconMappings;
        this.dashboardProperties = dashboardProperties;
    }

    public @NonNull String resolveIcon(@NonNull String imageName) {
        String iconName =
                switch (imageName) {
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
        return dashboardProperties.icons().url() + "/" + iconName
                + dashboardProperties.icons().extension();
    }
}
