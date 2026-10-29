package com.roomelephant.porthole.core.enrichment.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.core.model.Endpoint;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EndpointSelectionResolverTest {
    private static final Endpoint PORT =
            new Endpoint("published-port:8080", "published-port", "http://docker:8080/", false);
    private static final Endpoint OTHER_PORT =
            new Endpoint("published-port:9000", "published-port", "http://docker:9000/", false);
    private static final Endpoint ROOT = new Endpoint("traefik:root", "traefik", "https://app.example.com/", false);
    private static final Endpoint IMPLICIT_ROOT =
            new Endpoint("traefik:implicit", "traefik", "https://other.example.com?view=main#top", false);
    private static final Endpoint PATH =
            new Endpoint("traefik:path", "traefik", "https://app.example.com/download/", false);
    private static final Endpoint OTHER_PATH =
            new Endpoint("traefik:other", "traefik", "https://app.example.com/admin/", false);
    private static final SelectedEndpoint MISSING = new SelectedEndpoint("traefik:missing", "traefik", null);

    private final EndpointSelectionResolver resolver = new EndpointSelectionResolver();

    @ParameterizedTest(name = "{0}")
    @MethodSource("selectionCases")
    void shouldResolveWithoutChangingCandidatesOrSavedChoice(
            String description, List<Endpoint> candidates, SelectedEndpoint saved, List<Endpoint> expected) {
        var input = new ArrayList<>(candidates);
        var originalSelection = saved == null ? null : new SelectedEndpoint(saved.id(), saved.source(), saved.url());

        var resolved = resolver.resolve(input, saved);

        assertThat(resolved).containsExactlyElementsOf(expected);
        assertThat(resolved.stream().filter(Endpoint::selected).count()).isBetween(0L, 1L);
        assertThat(input).containsExactlyElementsOf(candidates);
        assertThat(saved).isEqualTo(originalSelection);
        assertThat(resolved).isNotSameAs(input);
        assertThatThrownBy(() -> resolved.add(PORT)).isInstanceOf(UnsupportedOperationException.class);
        input.clear();
        assertThat(resolved).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> selectionCases() {
        return Stream.of(
                Arguments.of(
                        "saved provider and ID override Traefik fallback",
                        List.of(ROOT, PORT),
                        new SelectedEndpoint("published-port:8080", "published-port", "http://stale:8080/"),
                        List.of(
                                ROOT,
                                new Endpoint("published-port:8080", "published-port", "http://docker:8080/", true))),
                Arguments.of(
                        "same ID with different provider does not match",
                        List.of(PORT, OTHER_PORT),
                        new SelectedEndpoint("published-port:8080", "traefik", null),
                        List.of(PORT, OTHER_PORT)),
                Arguments.of(
                        "same provider with different ID does not match",
                        List.of(PORT, OTHER_PORT),
                        new SelectedEndpoint("published-port:8000", "published-port", null),
                        List.of(PORT, OTHER_PORT)),
                Arguments.of(
                        "explicit none leaves available candidates unselected",
                        List.of(ROOT, PORT),
                        new SelectedEndpoint("none", "none", null),
                        List.of(ROOT, PORT)),
                Arguments.of(
                        "custom appends and selects only the configured URL",
                        List.of(ROOT, PORT),
                        new SelectedEndpoint("custom", "custom", "https://custom.example.com/app"),
                        List.of(ROOT, PORT, new Endpoint("custom", "custom", "https://custom.example.com/app", true))),
                Arguments.of(
                        "custom works without discovery",
                        List.of(),
                        new SelectedEndpoint("custom", "custom", "http://custom.example.com:9000"),
                        List.of(new Endpoint("custom", "custom", "http://custom.example.com:9000", true))),
                Arguments.of("no candidates remain unresolved", List.of(), null, List.of()),
                Arguments.of(
                        "unavailable saved choice with no candidates stays unresolved", List.of(), MISSING, List.of()),
                Arguments.of(
                        "single published candidate is selected automatically",
                        List.of(PORT),
                        null,
                        List.of(new Endpoint("published-port:8080", "published-port", "http://docker:8080/", true))),
                Arguments.of(
                        "multiple published candidates stay unresolved",
                        List.of(PORT, OTHER_PORT),
                        null,
                        List.of(PORT, OTHER_PORT)),
                Arguments.of(
                        "sole Traefik candidate wins among published candidates",
                        List.of(PORT, PATH, OTHER_PORT),
                        null,
                        List.of(
                                PORT,
                                new Endpoint("traefik:path", "traefik", "https://app.example.com/download/", true),
                                OTHER_PORT)),
                Arguments.of(
                        "unique explicit Traefik root wins",
                        List.of(PATH, PORT, ROOT),
                        null,
                        List.of(PATH, PORT, new Endpoint("traefik:root", "traefik", "https://app.example.com/", true))),
                Arguments.of(
                        "implicit empty path counts as a Traefik root",
                        List.of(PATH, IMPLICIT_ROOT, PORT),
                        null,
                        List.of(
                                PATH,
                                new Endpoint(
                                        "traefik:implicit", "traefik", "https://other.example.com?view=main#top", true),
                                PORT)),
                Arguments.of(
                        "multiple Traefik routes without roots stay unresolved",
                        List.of(PORT, PATH, OTHER_PATH),
                        null,
                        List.of(PORT, PATH, OTHER_PATH)),
                Arguments.of(
                        "explicit and implicit roots are ambiguous",
                        List.of(ROOT, IMPLICIT_ROOT, PATH),
                        null,
                        List.of(ROOT, IMPLICIT_ROOT, PATH)),
                Arguments.of(
                        "saved nonroot Traefik choice wins over root",
                        List.of(ROOT, PATH),
                        new SelectedEndpoint("traefik:path", "traefik", null),
                        List.of(
                                ROOT,
                                new Endpoint("traefik:path", "traefik", "https://app.example.com/download/", true))),
                Arguments.of(
                        "unavailable saved choice falls back to sole published candidate",
                        List.of(PORT),
                        MISSING,
                        List.of(new Endpoint("published-port:8080", "published-port", "http://docker:8080/", true))),
                Arguments.of(
                        "unavailable saved choice falls back to sole Traefik candidate",
                        List.of(PORT, PATH),
                        MISSING,
                        List.of(
                                PORT,
                                new Endpoint("traefik:path", "traefik", "https://app.example.com/download/", true))),
                Arguments.of(
                        "unavailable saved choice falls back to unique root",
                        List.of(PATH, ROOT),
                        MISSING,
                        List.of(PATH, new Endpoint("traefik:root", "traefik", "https://app.example.com/", true))),
                Arguments.of(
                        "unavailable saved choice cannot select arbitrary nonroot",
                        List.of(PATH, OTHER_PATH),
                        MISSING,
                        List.of(PATH, OTHER_PATH)),
                Arguments.of(
                        "unavailable saved choice cannot select arbitrary root",
                        List.of(ROOT, IMPLICIT_ROOT),
                        MISSING,
                        List.of(ROOT, IMPLICIT_ROOT)),
                Arguments.of(
                        "unavailable saved choice cannot select arbitrary published port",
                        List.of(PORT, OTHER_PORT),
                        MISSING,
                        List.of(PORT, OTHER_PORT)),
                Arguments.of(
                        "resolution clears stale selected flags",
                        List.of(
                                new Endpoint("published-port:8080", "published-port", "http://docker:8080/", true),
                                new Endpoint("traefik:root", "traefik", "https://app.example.com/", true)),
                        null,
                        List.of(PORT, new Endpoint("traefik:root", "traefik", "https://app.example.com/", true))));
    }
}
