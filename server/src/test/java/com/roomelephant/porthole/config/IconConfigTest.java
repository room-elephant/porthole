package com.roomelephant.porthole.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.config.properties.DashboardProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IconConfigTest {
    @TempDir
    Path directory;

    @Test
    void usesDefaultsWhenExternalFileIsMissing() {
        assertThat(load(directory.resolve("missing.yml")))
                .containsEntry("mongo", "mongodb")
                .containsEntry("postgres", "postgresql");
    }

    @Test
    void externalMappingsOverrideDefaultsAndAddNewIcons() throws IOException {
        var file = directory.resolve("icons.yml");
        Files.writeString(file, "postgres: custom-postgres\nmy-app: custom-app\n");

        assertThat(load(file))
                .containsEntry("mongo", "mongodb")
                .containsEntry("postgres", "custom-postgres")
                .containsEntry("my-app", "custom-app");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "# no overrides\n", "null", "broken: ["})
    void keepsDefaultsWhenExternalFileHasNoUsableMappings(String yaml) throws IOException {
        var file = directory.resolve("icons.yml");
        Files.writeString(file, yaml);

        assertThat(load(file)).containsEntry("postgres", "postgresql");
    }

    @Test
    void returnedMappingsAreImmutable() {
        var mappings = load(directory.resolve("missing.yml"));

        assertThatThrownBy(() -> mappings.put("postgres", "changed")).isInstanceOf(UnsupportedOperationException.class);
    }

    private Map<String, String> load(Path file) {
        return new IconConfig()
                .iconMappings(new DashboardProperties(
                        new DashboardProperties.Icons(file.toString(), "https://icons.example/", ".webp")));
    }
}
