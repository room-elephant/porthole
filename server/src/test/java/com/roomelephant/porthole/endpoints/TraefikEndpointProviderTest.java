package com.roomelephant.porthole.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TraefikEndpointProviderTest {
    private final TraefikEndpointProvider provider = new TraefikEndpointProvider(new TraefikRuleParser());

    @Test
    void shouldDiscoverHostOnlyTlsRouter() {
        Map<String, String> labels = Map.of(
                "traefik.http.routers.smh-transmission.rule",
                "Host(`transmission.example.com`)",
                "traefik.http.routers.smh-transmission.tls",
                "true",
                "traefik.http.services.smh-transmission.loadbalancer.server.port",
                "9091");
        assertThat(provider.discover(EndpointFixtures.target(Set.of(51413), labels)))
                .containsExactly(new Candidate(
                        "traefik:https://transmission.example.com/",
                        "traefik",
                        "https://transmission.example.com/",
                        CandidateKind.ROUTED));
    }

    @Test
    void shouldKeepRootAndDownloadRoutes() {
        Map<String, String> labels = Map.of(
                "traefik.http.routers.shelf.rule",
                "Host(`SHELF.example.com`)",
                "traefik.http.routers.shelf.tls",
                "true",
                "traefik.http.routers.download.rule",
                "Host(`shelf.example.com`) && PathPrefix(`/dl/`)",
                "traefik.http.routers.download.tls.certresolver",
                "letsencrypt",
                "traefik.http.routers.renamed.rule",
                "Host(`shelf.example.com`)",
                "traefik.http.routers.renamed.tls",
                "true");
        assertThat(provider.discover(EndpointFixtures.target(Set.of(), labels)))
                .extracting(Candidate::url)
                .containsExactly("https://shelf.example.com/", "https://shelf.example.com/dl/");
    }

    @Test
    void shouldHandleMixedCaseLabelKeys() {
        assertThat(provider.discover(EndpointFixtures.target(
                        Set.of(),
                        Map.of(
                                "TrAeFiK.HTTP.Routers.App.Rule",
                                "Host(`example.com`)",
                                "TRAEFIK.HTTP.ROUTERS.APP.TLS",
                                "true"))))
                .extracting(Candidate::url)
                .containsExactly("https://example.com/");
    }

    @Test
    void shouldDisableTraefikWithoutAffectingPublishedPorts() {
        Map<String, String> labels =
                Map.of("traefik.enable", "FALSE", "traefik.http.routers.app.rule", "Host(`example.com`)");
        EndpointTarget container = EndpointFixtures.target(Set.of(51413), labels);
        assertThat(provider.discover(container)).isEmpty();
        assertThat(new EndpointDiscovery(java.util.List.of(
                                provider, new PublishedPortEndpointProvider(new PublishedHostResolver())))
                        .discover(container))
                .extracting(Candidate::source)
                .containsExactly("published-port");
    }

    @ParameterizedTest
    @CsvSource({"true,false,https", "false,true,http", "absent,true,https", "absent,false,http"})
    void shouldInferSchemeFromRouterTls(String tls, boolean tlsSublabel, String scheme) {
        HashMap<String, String> labels = new HashMap<String, String>();
        labels.put("traefik.http.routers.app.rule", "Host(`example.com`)");
        labels.put("traefik.http.routers.app.entrypoints", "websecure");
        labels.put("traefik.http.services.app.loadbalancer.server.scheme", "https");
        labels.put("traefik.http.services.app.loadbalancer.server.port", "8443");
        if (!tls.equals("absent")) labels.put("traefik.http.routers.app.tls", tls);
        if (tlsSublabel) labels.put("traefik.http.routers.app.tls.certresolver", "letsencrypt");
        assertThat(provider.discover(EndpointFixtures.target(Set.of(), labels)))
                .extracting(Candidate::url)
                .containsExactly(scheme + "://example.com/");
    }

    @Test
    void shouldKeepOtherCandidatesWhenOneRouterIsInvalid() {
        Map<String, String> labels = Map.of(
                "traefik.http.routers.invalid.rule",
                "Host(`example.com`) && Method(`POST`)",
                "traefik.http.routers.valid.rule",
                "Host(`valid.example.com`)");
        assertThat(provider.discover(EndpointFixtures.target(Set.of(), labels)))
                .extracting(Candidate::url)
                .containsExactly("http://valid.example.com/");
    }

    @Test
    void shouldIgnoreTcpUdpAndMissingRule() {
        assertThat(provider.discover(EndpointFixtures.target(Set.of(), Map.of())))
                .isEmpty();
        assertThat(provider.discover(EndpointFixtures.target(
                        Set.of(),
                        Map.of(
                                "traefik.tcp.routers.app.rule",
                                "HostSNI(`example.com`)",
                                "traefik.udp.routers.app.entrypoints",
                                "udp",
                                "traefik.http.routers.app.tls",
                                "true"))))
                .isEmpty();
    }
}
