package com.roomelephant.porthole.core.enrichment.icon;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.mapping.ContainerMapper;
import com.roomelephant.porthole.core.model.ContainerSnapshot;
import com.roomelephant.porthole.inbound.web.mapper.ContainerDTOMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
}
