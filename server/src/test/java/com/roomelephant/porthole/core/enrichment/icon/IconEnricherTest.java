package com.roomelephant.porthole.core.enrichment.icon;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.mapping.ContainerMapper;
import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerSnapshot;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.inbound.web.mapper.ContainerDTOMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IconEnricherTest {
    @ParameterizedTest
    @CsvSource(
            value = {
                "nginx:1.25|nginx",
                "bitnami/postgresql:15|postgresql",
                "registry.example.com:5000/team/postgres:16|postgresql",
                "nginx@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa|nginx",
                "my_custom_app:latest|my-custom-app",
                "My_Custom_APP:latest|unknown",
                "NULL|unknown",
                "''|unknown"
            },
            delimiter = '|',
            nullValues = "NULL")
    void enrichesImageIconAndPreservesTheApiValue(String image, String expectedIcon) {
        var snapshot = new ContainerSnapshot("id", "app", image, Set.of(), Map.of(), "running", "Up");
        var container = new ContainerMapper().toModel(snapshot, "local");
        var resolver = new IconResolver(
                Map.of("postgres", "postgresql"), new IconSettings("https://cdn.example.com/icons", ".svg"));
        var enriched = new IconEnricher(resolver)
                .enrich(List.of(container), new EnrichmentContext("local", null, true, false));

        assertThat(enriched).singleElement().satisfies(result -> {
            assertThat(result).isSameAs(container);
            assertThat(result.iconUrl()).isEqualTo("https://cdn.example.com/icons/" + expectedIcon + ".svg");
            assertThat(new ContainerDTOMapper().toDTO(result).iconUrl()).isEqualTo(result.iconUrl());
        });
    }

    private String enrichedIconUrl(ContainerPreferences preferences) {
        var snapshot = new ContainerSnapshot("id", "app", "nginx:1.25", Set.of(), Map.of(), "running", "Up");
        var container = new ContainerMapper().toModel(snapshot, "local");
        container.setPreferences(preferences);
        var resolver = new IconResolver(Map.of(), new IconSettings("https://cdn.example.com/icons", ".svg"));
        return new IconEnricher(resolver)
                .enrich(List.of(container), new EnrichmentContext("local", null, true, false))
                .getFirst()
                .iconUrl();
    }

    @Test
    void libraryChoiceOverridesImageIcon() {
        assertThat(enrichedIconUrl(new ContainerPreferences(null, SelectedIcon.library("coredns"))))
                .isEqualTo("https://cdn.example.com/icons/coredns.svg");
    }

    @Test
    void uploadChoiceUsesRelativePath() {
        String name = "0123456789abcdef0123456789abcdef.webp";
        assertThat(enrichedIconUrl(new ContainerPreferences(null, SelectedIcon.upload(name))))
                .isEqualTo("/api/icons/" + name);
    }

    @Test
    void noChoiceKeepsImageBasedIcon() {
        assertThat(enrichedIconUrl(new ContainerPreferences(null, null)))
                .isEqualTo("https://cdn.example.com/icons/nginx.svg");
    }

    @Test
    void missingUploadFileStillResolvesPath() {
        // The enricher has no file store dependency, so inventory never touches the disk.
        String name = "ffffffffffffffffffffffffffffffff.png";
        assertThat(enrichedIconUrl(new ContainerPreferences(null, SelectedIcon.upload(name))))
                .isEqualTo("/api/icons/" + name);
    }
}
