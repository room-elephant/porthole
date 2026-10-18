package com.roomelephant.porthole.outbound.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GhcrRegistryTest {
    private static final String IMAGE = "ghcr.io/acme/app:1.0";
    private static final String TOKEN = "https://ghcr.io/token?service=ghcr.io&scope=repository:acme/app:pull";
    private static final String TAGS = "https://ghcr.io/v2/acme/app/tags/list?n=100";
    private static final String A = "sha256:" + "a".repeat(64);
    private static final String B = "sha256:" + "b".repeat(64);
    private MockRestServiceServer server;
    private RegistryOperationsAdapter service;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new RegistryProperties(
                new RegistryProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(10)),
                new RegistryProperties.Cache(Duration.ofMinutes(5), 100),
                new RegistryProperties.DockerHub(
                        "https://hub-registry/v2/", "https://hub-auth/", "https://hub-catalog/"),
                ghcr());
        service = RegistryTestClients.operations(builder.build(), properties);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "?n=100&last=1.0",
                "/v2/acme/app/tags/list?n=100&last=1.0",
                "https://ghcr.io/v2/acme/app/tags/list?n=100&last=1.0"
            })
    void readsEveryGhcrPageAndKeepsHubCatalogueSeparate(String next) {
        auth("token", "ghcr-token");
        server.expect(requestTo(TAGS))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghcr-token"))
                .andRespond(
                        withSuccess("{\"name\":\"acme/app\",\"tags\":[\"1.0\",\"latest\"]}", MediaType.APPLICATION_JSON)
                                .header(HttpHeaders.LINK, "<" + next + ">; rel=\"next\""));
        server.expect(requestTo("https://ghcr.io/v2/acme/app/tags/list?n=100&last=1.0"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghcr-token"))
                .andRespond(withSuccess(
                        "{\"name\":\"acme/app\",\"tags\":[\"2.0\",\"1.0-alpine\"]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://hub-catalog/acme/app/tags?page_size=100"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"results\":[{\"name\":\"9.0\"}]}", MediaType.APPLICATION_JSON));
        assertEquals("2.0", latest(IMAGE));
        assertEquals("9.0", latest("acme/app:1.0"));
        assertEquals("2.0", latest("GHCR.IO/acme/app:latest"));
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"application/vnd.oci.image.index.v1+json,true", "application/vnd.oci.image.manifest.v1+json,false"})
    void ghcrManifestEvidenceUsesItsOwnAuthAndRegistry(String localType, boolean changed) {
        auth("access_token", "ghcr-token");
        manifest("1.0", B, "application/vnd.oci.image.index.v1+json", "ghcr-token");
        manifest(A, A, localType, "ghcr-token");
        assertEquals(changed, changed(IMAGE, List.of(A)));
        server.verify();
    }

    @Test
    void anonymousTokensAreReusedAcrossOperations() {
        auth("token", "first");
        manifest("1.0", A, "application/vnd.oci.image.index.v1+json", "first");
        manifest("1.0", A, "application/vnd.oci.image.index.v1+json", "first");
        assertFalse(changed(IMAGE, List.of(A)));
        assertFalse(changed(IMAGE, List.of(A)));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "title=\"x; rel=prev\"; rel=next",
                "rel=\"prev next\"; title=\"x, y\"",
                "title=\"a\\\"b\"; rel=next"
            })
    void quotedParametersCannotHideTheNextPage(String parameters) {
        auth("token", "ghcr-token");
        server.expect(requestTo(TAGS))
                .andRespond(withSuccess("{\"tags\":[\"1.0\"]}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<?n=100&last=1.0>; " + parameters));
        server.expect(requestTo("https://ghcr.io/v2/acme/app/tags/list?n=100&last=1.0"))
                .andRespond(withSuccess("{\"tags\":[\"2.0\"]}", MediaType.APPLICATION_JSON));
        assertEquals("2.0", latest(IMAGE));
        assertEquals("2.0", latest(IMAGE));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<https://evil.example/tags>; rel=\"next\"",
                "</v2/other/app/tags/list?n=100>; rel=next",
                "<?n=100>; rel=\"next\"",
                "<?n=100&last=a>; rel=next, <?n=100&last=b>; rel=next",
                "<?n=100&last=1.0>; rel=prev; rel=next",
                "<?n=100&last=1.0>; rel=\"next\" garbage",
                "not-a-link"
            })
    void incompleteOrUnsafeGhcrCatalogueHasNoMaximum(String link) {
        auth("token", "ghcr-token");
        server.expect(requestTo(TAGS))
                .andRespond(withSuccess("{\"tags\":[\"9.0\"]}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, link));
        assertInstanceOf(
                com.roomelephant.porthole.core.model.RegistryLookup.Unavailable.class, service.listTags(ref(IMAGE)));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 404, 429})
    void laterPageFailureDoesNotExposeAPartialMaximum(int status) {
        auth("token", "ghcr-token");
        server.expect(requestTo(TAGS))
                .andRespond(withSuccess("{\"tags\":[\"9.0\"]}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<?n=100&last=9.0>; rel=next"));
        server.expect(requestTo("https://ghcr.io/v2/acme/app/tags/list?n=100&last=9.0"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        if (status == 401) {
            auth("token", "refreshed");
            server.expect(requestTo("https://ghcr.io/v2/acme/app/tags/list?n=100&last=9.0"))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        }
        assertInstanceOf(
                com.roomelephant.porthole.core.model.RegistryLookup.Unavailable.class, service.listTags(ref(IMAGE)));
        server.verify();
    }

    @Test
    void deniedAnonymousAccessIsInconclusive() {
        server.expect(requestTo(TOKEN)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertInstanceOf(
                com.roomelephant.porthole.core.model.RegistryLookup.Unavailable.class, service.listTags(ref(IMAGE)));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"tags\":null}",
                "{\"tags\":\"1.0\"}",
                "{\"tags\":[]}",
                "{\"tags\":[null]}",
                "{\"tags\":[42]}",
                "{\"tags\":[\"2.0\",null]}"
            })
    void malformedOrEmptyTagListsDoNotSupplyAVersion(String body) {
        auth("token", "ghcr-token");
        server.expect(requestTo(TAGS)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        if (body.equals("{\"tags\":[]}")) assertNull(latest(IMAGE));
        else
            assertInstanceOf(
                    com.roomelephant.porthole.core.model.RegistryLookup.Unavailable.class,
                    service.listTags(ref(IMAGE)));
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"'[]',1.0", "'[\"v1.0.0\"]',v1.0.0"})
    void emptyFinalPagesAndEquivalentAliasesHaveADeterministicMaximum(String tags, String expected) {
        auth("token", "ghcr-token");
        server.expect(requestTo(TAGS))
                .andRespond(withSuccess("{\"tags\":[\"1.0\"]}", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<?n=100&last=1.0>; rel=next"));
        server.expect(requestTo("https://ghcr.io/v2/acme/app/tags/list?n=100&last=1.0"))
                .andRespond(withSuccess("{\"tags\":" + tags + "}", MediaType.APPLICATION_JSON));
        assertEquals(expected, latest(IMAGE));
        server.verify();
    }

    private void auth(String field, String token) {
        server.expect(requestTo(TOKEN))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"" + field + "\":\"" + token + "\"}", MediaType.APPLICATION_JSON));
    }

    private void manifest(String reference, String digest, String type, String token) {
        server.expect(requestTo("https://ghcr.io/v2/acme/app/manifests/" + reference))
                .andExpect(method(HttpMethod.HEAD))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andRespond(withSuccess("", MediaType.parseMediaType(type)).header("Docker-Content-Digest", digest));
    }

    private static com.roomelephant.porthole.core.model.ImageReference ref(String image) {
        return com.roomelephant.porthole.core.model.ImageReference.tryParse(image)
                .orElseThrow();
    }

    private String latest(String image) {
        var result = service.listTags(ref(image));
        var available = assertInstanceOf(com.roomelephant.porthole.core.model.RegistryLookup.Available.class, result);
        return new com.roomelephant.porthole.core.enrichment.version.resolution.ReleasePolicy()
                .latest((com.roomelephant.porthole.core.model.TagCatalogue) available.value());
    }

    private boolean changed(String image, List<String> digests) {
        return new com.roomelephant.porthole.core.enrichment.version.resolution.UpdatePolicy()
                        .manifest(digests, service.readManifests(ref(image), digests))
                == com.roomelephant.porthole.core.enrichment.version.resolution.UpdatePolicy.Evidence.POSITIVE;
    }

    private static RegistryProperties.Ghcr ghcr() {
        return new RegistryProperties.Ghcr(
                "https://ghcr.io/v2/", "https://ghcr.io/token?service=ghcr.io&scope=repository:");
    }
}
