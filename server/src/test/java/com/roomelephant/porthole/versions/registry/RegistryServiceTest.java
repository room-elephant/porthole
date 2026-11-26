package com.roomelephant.porthole.versions.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.roomelephant.porthole.shared.ImageReference;
import com.roomelephant.porthole.versions.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Docker Hub tag catalogues, read from the registry API. */
class RegistryServiceTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final RegistryOperationsAdapter adapter = RegistryTestClients.operations(
            builder.build(),
            new RegistryProperties(
                    new RegistryProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(10)),
                    new RegistryProperties.Cache(Duration.ofMinutes(5), 100),
                    new RegistryProperties.DockerHub("https://registry/v2/", "https://auth/"),
                    new RegistryProperties.Ghcr("https://ghcr/v2/", "https://ghcr/auth/")));
    private static final ImageReference IMAGE =
            ImageReference.tryParse("nginx:1.0").orElseThrow();
    private static final String TOKEN = "https://auth/library/nginx:pull";
    private static final String URL = "https://registry/v2/library/nginx/tags/list?n=1000";
    private static final String SECOND = URL + "&last=1.0";

    @ParameterizedTest
    @ValueSource(
            strings = {
                "?n=1000&last=1.0",
                "/v2/library/nginx/tags/list?n=1000&last=1.0",
                "https://registry/v2/library/nginx/tags/list?n=1000&last=1.0"
            })
    void followsAllPagesWithTheAnonymousTokenAndReturnsRawTags(String next) {
        token();
        server.expect(requestTo(URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer hub-token"))
                .andRespond(withSuccess(
                                "{\"name\":\"library/nginx\",\"tags\":[\"latest\",\"1.0\"]}",
                                MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<" + next + ">; rel=\"next\""));
        server.expect(requestTo(SECOND))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer hub-token"))
                .andRespond(withSuccess("{\"tags\":[\"1.0-alpine\",\"2147483648.0\"]}", MediaType.APPLICATION_JSON));
        assertEquals(
                new RegistryLookup.Available<>(
                        new TagCatalogue(List.of("latest", "1.0", "1.0-alpine", "2147483648.0"))),
                adapter.listTags(IMAGE));
        server.verify();
    }

    @Test
    void catalogueLargerThanTheHubWebsiteAllowsIsRead() {
        // hub.docker.com stops anonymous clients at 1000 tags; the registry has no such limit.
        List<String> first = new ArrayList<>();
        for (int i = 0; i < 1000; i++) first.add("1." + i);
        token();
        server.expect(requestTo(URL))
                .andRespond(withSuccess(tags(first), MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<?n=1000&last=1.999>; rel=\"next\""));
        server.expect(requestTo(URL + "&last=1.999"))
                .andRespond(withSuccess(tags(List.of("2.0")), MediaType.APPLICATION_JSON));
        RegistryLookup<TagCatalogue> result = adapter.listTags(IMAGE);
        TagCatalogue catalogue = (TagCatalogue)
                assertInstanceOf(RegistryLookup.Available.class, result).value();
        assertEquals(1001, catalogue.tags().size());
        assertEquals("2.0", new ReleasePolicy().latest(catalogue));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "not-json", "{}", "{\"tags\":null}", "{\"tags\":[42]}"})
    void rejectsInvalidResponses(String body) {
        token();
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertInstanceOf(RegistryLookup.Unavailable.class, adapter.listTags(IMAGE));
        server.verify();
    }

    @Test
    void first404IsMissingButLater404IsUnavailable() {
        token();
        server.expect(requestTo(URL)).andRespond(withResourceNotFound());
        assertInstanceOf(RegistryLookup.Missing.class, adapter.listTags(IMAGE));
        server.verify();
        server.reset();
        adapter.invalidateCache();
        server.expect(requestTo(URL))
                .andRespond(withSuccess(tags(List.of("9.0")), MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<?n=1000&last=1.0>; rel=next"));
        server.expect(requestTo(SECOND)).andRespond(withResourceNotFound());
        assertInstanceOf(RegistryLookup.Unavailable.class, adapter.listTags(IMAGE));
        server.verify();
    }

    @Test
    void refusedRequestsSayWhichStatusTheRegistrySent() {
        token();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertEquals(new RegistryLookup.Unavailable<>("Registry responded 429"), adapter.listTags(IMAGE));
        server.verify();
    }

    @Test
    void malformedListsCannotBecomeSuccessfulEmptyCatalogue() {
        token();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"tags\":[null]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(tags(List.of("3.0")), MediaType.APPLICATION_JSON));
        assertInstanceOf(RegistryLookup.Unavailable.class, adapter.listTags(IMAGE));
        adapter.invalidateCache();
        assertEquals(new RegistryLookup.Available<>(new TagCatalogue(List.of("3.0"))), adapter.listTags(IMAGE));
        server.verify();
    }

    private void token() {
        server.expect(requestTo(TOKEN))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"token\":\"hub-token\"}", MediaType.APPLICATION_JSON));
    }

    private static String tags(List<String> tags) {
        StringJoiner json = new StringJoiner("\",\"", "{\"tags\":[\"", "\"]}");
        tags.forEach(json::add);
        return json.toString();
    }
}
