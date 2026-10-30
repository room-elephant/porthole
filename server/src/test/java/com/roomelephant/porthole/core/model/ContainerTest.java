package com.roomelephant.porthole.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContainerTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("resolvedAvailability")
    void shouldDeriveWebUiFromCurrentCandidatesAndInternalNoneChoice(
            List<Endpoint> endpoints, SelectedEndpoint selection, boolean expected) {
        var container = new Container(
                "id",
                "local",
                "app",
                "app",
                "app:latest",
                new Container.Details(Set.of(), Map.of(), null, "exited", "Exited"),
                endpoints);
        container.setPreferences(new ContainerPreferences(selection));
        assertThat(container.hasWebUi()).isEqualTo(expected);
        container.setUrlCandidates(List.of());
        assertThat(container.hasWebUi()).isFalse();
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> resolvedAvailability() {
        var port = new Endpoint("published-port:80", "published-port", "http://app/", true);
        var root = new Endpoint("traefik:root", "traefik", "https://app/", false);
        var custom = new Endpoint("custom", "custom", "https://custom/", true);
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(List.of(), null, false),
                org.junit.jupiter.params.provider.Arguments.of(List.of(port), null, true),
                org.junit.jupiter.params.provider.Arguments.of(
                        List.of(root), new SelectedEndpoint("missing", "traefik", null), true),
                org.junit.jupiter.params.provider.Arguments.of(
                        List.of(root, new Endpoint("other", "traefik", "https://other/", false)), null, true),
                org.junit.jupiter.params.provider.Arguments.of(
                        List.of(custom), new SelectedEndpoint("custom", "custom", "https://custom/"), true),
                org.junit.jupiter.params.provider.Arguments.of(
                        List.of(root), new SelectedEndpoint("none", "none", null), false),
                org.junit.jupiter.params.provider.Arguments.of(
                        List.of(), new SelectedEndpoint("custom", "custom", "https://stale/"), false));
    }

    @Test
    void shouldReplaceUrlCandidatesWithoutSharingMutableLists() {
        Endpoint original = new Endpoint("original", "traefik", "https://original.example.com/", false);
        List<Endpoint> initial = new ArrayList<>(List.of(original));
        Container container = new Container(
                "id",
                "local",
                "app",
                "app",
                "app:latest",
                new Container.Details(Set.of(8080), Map.of("custom.metadata", "retained"), null, "running", "Up"),
                initial);
        initial.clear();
        assertThat(container.urlCandidates()).containsExactly(original);
        List<Endpoint> beforeEnrichment = container.urlCandidates();

        Endpoint discovered = new Endpoint("discovered", "traefik", "https://app.example.com/", false);
        List<Endpoint> candidates = new ArrayList<>(List.of(discovered));
        container.setUrlCandidates(candidates);
        candidates.clear();

        assertThat(container.urlCandidates()).containsExactly(discovered);
        assertThat(beforeEnrichment).containsExactly(original);
        assertThat(container.labels()).containsEntry("custom.metadata", "retained");
        assertThat(container.publishedPorts()).containsExactly(8080);
        var immutableCandidates = container.urlCandidates();
        assertThatThrownBy(immutableCandidates::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
