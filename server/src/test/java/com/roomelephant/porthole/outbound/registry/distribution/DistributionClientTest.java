package com.roomelephant.porthole.outbound.registry.distribution;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomelephant.porthole.core.model.*;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DistributionClientTest {
    private static final ImageReference IMAGE =
            ImageReference.tryParse("ghcr.io/acme/app:latest").orElseThrow();
    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private MockRestServiceServer server;
    private DistributionClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var http = builder.build();
        var json = new ObjectMapper();
        client = new DistributionClient(
                http, json, "https://registry/", new BearerTokenClient(http, json, "https://auth/", Clock.systemUTC()));
    }

    private void auth() {
        server.expect(requestTo("https://auth/acme/app:pull"))
                .andRespond(withSuccess("{\"token\":\"test\"}", MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest
    @CsvSource({
        "application/vnd.oci.image.index.v1+json,INDEX",
        "application/vnd.docker.distribution.manifest.list.v2+json,INDEX",
        "application/vnd.oci.image.manifest.v1+json,IMAGE",
        "application/vnd.docker.distribution.manifest.v2+json,IMAGE",
        "text/plain,UNKNOWN"
    })
    void returnsManifestFacts(String type, ManifestMetadata.Kind kind) {
        auth();
        server.expect(requestTo("https://registry/acme/app/manifests/latest"))
                .andExpect(method(HttpMethod.HEAD))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test"))
                .andRespond(withSuccess("", MediaType.parseMediaType(type)).header("Docker-Content-Digest", DIGEST));
        assertEquals(
                new RegistryLookup.Available<>(new ManifestMetadata(DIGEST, kind)),
                client.readManifest(IMAGE, "latest"));
        server.verify();
    }

    @Test
    void resource404AndAuth404HaveDifferentMeaning() {
        auth();
        server.expect(requestTo("https://registry/acme/app/manifests/latest")).andRespond(withResourceNotFound());
        assertInstanceOf(RegistryLookup.Missing.class, client.readManifest(IMAGE, "latest"));
        server.verify();
        server.reset();
        server.expect(requestTo("https://auth/acme/app:pull")).andRespond(withResourceNotFound());
        var builder = RestClient.builder();
        var otherServer = MockRestServiceServer.bindTo(builder).build();
        var http = builder.build();
        var json = new ObjectMapper();
        otherServer.expect(requestTo("https://auth/acme/app:pull")).andRespond(withResourceNotFound());
        var other = new DistributionClient(
                http, json, "https://registry/", new BearerTokenClient(http, json, "https://auth/", Clock.systemUTC()));
        assertInstanceOf(RegistryLookup.Unavailable.class, other.readManifest(IMAGE, "latest"));
        otherServer.verify();
    }

    @Test
    void invalidDigestIsUnavailable() {
        auth();
        server.expect(requestTo("https://registry/acme/app/manifests/latest"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON).header("Docker-Content-Digest", "bad"));
        assertInstanceOf(RegistryLookup.Unavailable.class, client.readManifest(IMAGE, "latest"));
        server.verify();
    }

    @Test
    void invalidContentTypeIsUnavailable() {
        auth();
        server.expect(requestTo("https://registry/acme/app/manifests/latest"))
                .andRespond(withSuccess()
                        .header("Content-Type", "not-a-media-type")
                        .header("Docker-Content-Digest", DIGEST));
        assertInstanceOf(RegistryLookup.Unavailable.class, client.readManifest(IMAGE, "latest"));
        server.verify();
    }
}
