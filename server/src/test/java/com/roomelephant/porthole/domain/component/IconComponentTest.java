package com.roomelephant.porthole.domain.component;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.roomelephant.porthole.config.properties.DashboardProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IconComponent")
class IconComponentTest {

    @Mock
    private DashboardProperties dashboardProperties;

    @Mock
    private DashboardProperties.Icons icons;

    private Map<String, String> iconMappings;
    private IconComponent iconComponent;

    @BeforeEach
    void setUp() {
        iconMappings = new HashMap<>();
        when(dashboardProperties.icons()).thenReturn(icons);
        when(icons.url()).thenReturn("https://cdn.example.com/icons");
        when(icons.extension()).thenReturn(".svg");
        iconComponent = new IconComponent(iconMappings, dashboardProperties);
    }

    @Nested
    @DisplayName("resolveIcon")
    class ResolveIcon {

        @Test
        @DisplayName("should return mapped icon URL when mapping exists")
        void shouldReturnMappedIconUrlWhenMappingExists() {
            iconMappings.put("postgres", "postgresql");

            String result = iconComponent.resolveIcon("postgres");

            assertEquals("https://cdn.example.com/icons/postgresql.svg", result);
        }

        @Test
        @DisplayName("should return sanitized icon URL when no mapping exists")
        void shouldReturnSanitizedIconUrlWhenNoMappingExists() {
            String result = iconComponent.resolveIcon("nginx");

            assertEquals("https://cdn.example.com/icons/nginx.svg", result);
        }

        @Test
        @DisplayName("should convert uppercase to lowercase")
        void shouldConvertUppercaseToLowercase() {
            String result = iconComponent.resolveIcon("PostgreSQL");

            assertEquals("https://cdn.example.com/icons/postgresql.svg", result);
        }

        @Test
        @DisplayName("should replace underscores with hyphens")
        void shouldReplaceUnderscoresWithHyphens() {
            String result = iconComponent.resolveIcon("my_custom_app");

            assertEquals("https://cdn.example.com/icons/my-custom-app.svg", result);
        }

        @Test
        @DisplayName("should handle image names with mixed case and underscores")
        void shouldHandleImageNamesWithMixedCaseAndUnderscores() {
            String result = iconComponent.resolveIcon("My_Custom_APP");

            assertEquals("https://cdn.example.com/icons/my-custom-app.svg", result);
        }

        @Test
        @DisplayName("should use mapping over sanitization")
        void shouldUseMappingOverSanitization() {
            iconMappings.put("My_Custom_APP", "custom-icon");

            String result = iconComponent.resolveIcon("My_Custom_APP");

            assertEquals("https://cdn.example.com/icons/custom-icon.svg", result);
        }

        @Test
        @DisplayName("should return default docker icon for empty image name")
        void shouldReturnDefaultDockerIconForEmptyImageName() {
            String result = iconComponent.resolveIcon("");

            assertEquals("https://cdn.example.com/icons/docker.svg", result);
        }

        @Test
        @DisplayName("should return default docker icon for blank image name")
        void shouldReturnDefaultDockerIconForBlankImageName() {
            String result = iconComponent.resolveIcon("   ");

            assertEquals("https://cdn.example.com/icons/docker.svg", result);
        }

        @Test
        @DisplayName("should handle image name with hyphens")
        void shouldHandleImageNameWithHyphens() {
            String result = iconComponent.resolveIcon("my-app");

            assertEquals("https://cdn.example.com/icons/my-app.svg", result);
        }
    }
}
