package com.roomelephant.porthole.outbound.registry;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.ImageReference;
import com.roomelephant.porthole.outbound.registry.dockerhub.DockerHubClient;
import com.roomelephant.porthole.outbound.registry.ghcr.GhcrClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RegistryProviderAuthenticationTest {
    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final ImageReference HUB =
            ImageReference.tryParse("nginx:latest").orElseThrow();
    private static final ImageReference GHCR =
            ImageReference.tryParse("ghcr.io/acme/app:1.0").orElseThrow();
    private MockRestServiceServer server;
    private RegistryClient hub;
    private RegistryClient ghcr;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var rest = builder.build();
        hub = new DockerHubClient(
                rest,
                new RegistryProperties.DockerHub("https://hub/", "https://hub-auth/", "https://hub-catalog/"),
                Duration.ofSeconds(5),
                new RegistryProperties.Cache(Duration.ofMinutes(5), 100));
        ghcr = new GhcrClient(
                rest, new RegistryProperties.Ghcr("https://ghcr/", "https://ghcr-auth/"), Duration.ofSeconds(5));
    }

    @Test
    void providersClaimOnlyTheirSupportedMutableReferences() {
        assertThat(hub.supports(HUB)).isTrue();
        assertThat(hub.supports(GHCR)).isFalse();
        assertThat(ghcr.supports(GHCR)).isTrue();
        assertThat(ghcr.supports(HUB)).isFalse();
        var pin = ImageReference.tryParse("ghcr.io/acme/app@" + DIGEST).orElseThrow();
        assertThat(hub.supports(pin)).isFalse();
        assertThat(ghcr.supports(pin)).isFalse();
    }

    @Test
    void emptyGhcrCatalogueResponseIsInconclusive() {
        server.expect(requestTo("https://ghcr-auth/acme/app:pull"))
                .andRespond(withSuccess("{\"token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://ghcr/acme/app/tags/list?n=100")).andRespond(withSuccess());
        assertThatThrownBy(() -> ghcr.getLatestVersion(GHCR)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"token", "access_token"})
    void hubReusesItsRepositoryToken(String field) {
        server.expect(requestTo("https://hub-auth/library/nginx:pull"))
                .andRespond(withSuccess("{\"" + field + "\":\"hub-token\"}", MediaType.APPLICATION_JSON));
        for (int i = 0; i < 2; i++) manifest("https://hub/library/nginx/manifests/latest", "hub-token");
        assertThat(hub.isTagChanged(HUB, List.of(DIGEST))).isFalse();
        assertThat(hub.isTagChanged(HUB, List.of(DIGEST))).isFalse();
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
        "hub,{}",
        "hub,{\"token\":42}",
        "hub,{\"token\":\"\"}",
        "ghcr,{}",
        "ghcr,{\"access_token\":42}",
        "ghcr,{\"token\":\"\"}"
    })
    void malformedTokensCannotSupplyManifestEvidence(String provider, String body) {
        boolean dockerHub = provider.equals("hub");
        server.expect(requestTo(dockerHub ? "https://hub-auth/library/nginx:pull" : "https://ghcr-auth/acme/app:pull"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> (dockerHub ? hub : ghcr).isTagChanged(dockerHub ? HUB : GHCR, List.of(DIGEST)))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub", "ghcr"})
    void invalidJsonCannotSupplyManifestEvidence(String provider) {
        boolean dockerHub = provider.equals("hub");
        server.expect(requestTo(dockerHub ? "https://hub-auth/library/nginx:pull" : "https://ghcr-auth/acme/app:pull"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> (dockerHub ? hub : ghcr).isTagChanged(dockerHub ? HUB : GHCR, List.of(DIGEST)))
                .isInstanceOf(java.io.UncheckedIOException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub", "ghcr"})
    void missingTokenResponseCannotSupplyManifestEvidence(String provider) {
        boolean dockerHub = provider.equals("hub");
        server.expect(requestTo(dockerHub ? "https://hub-auth/library/nginx:pull" : "https://ghcr-auth/acme/app:pull"))
                .andRespond(withSuccess());
        assertThatThrownBy(() -> (dockerHub ? hub : ghcr).isTagChanged(dockerHub ? HUB : GHCR, List.of(DIGEST)))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    private void manifest(String url, String token) {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.HEAD))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andRespond(withSuccess("", MediaType.parseMediaType("application/vnd.oci.image.index.v1+json"))
                        .header("Docker-Content-Digest", DIGEST));
    }
}
