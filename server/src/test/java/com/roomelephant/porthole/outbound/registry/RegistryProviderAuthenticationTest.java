package com.roomelephant.porthole.outbound.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.roomelephant.porthole.config.RegistryConfig;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class RegistryProviderAuthenticationTest {
    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final RestClient http = builder.build();
    private final RegistryProperties properties = new RegistryProperties(
            new RegistryProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(10)),
            new RegistryProperties.Cache(Duration.ofMinutes(1), 100),
            new RegistryProperties.DockerHub("https://hub/", "https://hub-auth/", "https://hub-catalog/"),
            new RegistryProperties.Ghcr("https://ghcr/", "https://ghcr-auth/"));

    private RegistryClient client(String provider) {
        var config = new RegistryConfig();
        return provider.equals("hub")
                ? config.dockerHubClient(http, JsonMapper.builder().build(), properties)
                : config.ghcrClient(http, JsonMapper.builder().build(), properties);
    }

    private static ImageReference image(String provider) {
        return ImageReference.tryParse(provider.equals("hub") ? "nginx:latest" : "ghcr.io/acme/app:latest")
                .orElseThrow();
    }

    private static String repo(String provider) {
        return provider.equals("hub") ? "library/nginx" : "acme/app";
    }

    private void token(String provider, String body) {
        server.expect(requestTo("https://" + provider + "-auth/" + repo(provider) + ":pull"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void manifest(String provider, String token) {
        server.expect(requestTo("https://" + provider + "/" + repo(provider) + "/manifests/latest"))
                .andExpect(method(HttpMethod.HEAD))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andRespond(withSuccess("", MediaType.parseMediaType("application/vnd.oci.image.index.v1+json"))
                        .header("Docker-Content-Digest", DIGEST));
    }

    @Test
    void providersClaimOnlyTheirMutableReferences() {
        var hub = client("hub");
        var ghcr = client("ghcr");
        assertTrue(hub.supports(image("hub")));
        assertFalse(hub.supports(image("ghcr")));
        assertTrue(ghcr.supports(image("ghcr")));
        assertFalse(ghcr.supports(image("hub")));
        assertFalse(ghcr.supports(ImageReference.tryParse("ghcr.io/app").orElseThrow()));
        assertFalse(ghcr.supports(
                ImageReference.tryParse("ghcr.io/acme/app@" + DIGEST).orElseThrow()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub", "ghcr"})
    void providersReuseRepositoryTokens(String provider) {
        token(provider, "{\"access_token\":\"test\"}");
        manifest(provider, "test");
        manifest(provider, "test");
        var client = client(provider);
        assertInstanceOf(RegistryLookup.Available.class, client.readManifest(image(provider), "latest"));
        assertInstanceOf(RegistryLookup.Available.class, client.readManifest(image(provider), "latest"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub", "ghcr"})
    void providersRefreshRejectedTokensOnce(String provider) {
        token(provider, "{\"token\":\"rejected\"}");
        server.expect(requestTo("https://" + provider + "/" + repo(provider) + "/manifests/latest"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        token(provider, "{\"token\":\"fresh\"}");
        manifest(provider, "fresh");
        assertInstanceOf(RegistryLookup.Available.class, client(provider).readManifest(image(provider), "latest"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub", "ghcr"})
    void invalidTokensSupplyUnavailableEvidence(String provider) {
        token(provider, "{\"token\":42}");
        assertInstanceOf(RegistryLookup.Unavailable.class, client(provider).readManifest(image(provider), "latest"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub", "ghcr"})
    void expiredTokensAreNotReused(String provider) {
        token(provider, "{\"token\":\"expired\",\"expires_in\":0}");
        manifest(provider, "expired");
        token(provider, "{\"token\":\"fresh\"}");
        manifest(provider, "fresh");
        var client = client(provider);
        assertInstanceOf(RegistryLookup.Available.class, client.readManifest(image(provider), "latest"));
        assertInstanceOf(RegistryLookup.Available.class, client.readManifest(image(provider), "latest"));
        server.verify();
    }
}
