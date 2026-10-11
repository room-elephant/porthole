package com.roomelephant.porthole.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContainerTest {
    @Test
    void shouldReplaceUrlCandidatesWithoutSharingMutableLists() {
        Endpoint original = new Endpoint("original", "traefik", "https://original.example.com/");
        List<Endpoint> initial = new ArrayList<>(List.of(original));
        Container container = new Container(
                "id",
                "app",
                "app",
                "app:latest",
                Set.of(8080),
                Map.of("custom.metadata", "retained"),
                initial,
                "icon",
                null,
                "running",
                "Up");
        initial.clear();
        assertThat(container.urlCandidates()).containsExactly(original);
        List<Endpoint> beforeEnrichment = container.urlCandidates();

        Endpoint discovered = new Endpoint("discovered", "traefik", "https://app.example.com/");
        List<Endpoint> candidates = new ArrayList<>(List.of(discovered));
        container.setUrlCandidates(candidates);
        candidates.clear();

        assertThat(container.urlCandidates()).containsExactly(discovered);
        assertThat(beforeEnrichment).containsExactly(original);
        assertThat(container.labels()).containsEntry("custom.metadata", "retained");
        assertThat(container.publishedPorts()).containsExactly(8080);
        assertThatThrownBy(() -> container.urlCandidates().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
