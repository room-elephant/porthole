package com.roomelephant.porthole.outbound.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RegistryServiceTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final RegistryOperationsAdapter adapter = RegistryTestClients.operations(
            builder.build(),
            new RegistryProperties(
                    new RegistryProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(10)),
                            new RegistryProperties.Cache(Duration.ofMinutes(5), 100),
                    new RegistryProperties.DockerHub("https://registry/v2/", "https://auth/", "https://repositories/"),
                            new RegistryProperties.Ghcr("https://ghcr/v2/", "https://ghcr/auth/")));
    private static final ImageReference IMAGE =
            ImageReference.tryParse("nginx:1.0").orElseThrow();
    private static final String URL = "https://repositories/library/nginx/tags?page_size=100";

    @ParameterizedTest
    @ValueSource(strings = {"?page=2", "/library/nginx/tags?page=2", "https://repositories/library/nginx/tags?page=2"})
    void followsAllPagesAndReturnsRawTags(String next) {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"results\":[{\"name\":\"latest\"},{\"name\":\"1.0\"}],\"next\":\"" + next + "\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://repositories/library/nginx/tags?page=2"))
                .andRespond(withSuccess(
                        "{\"results\":[{\"name\":\"1.0-alpine\"},{\"name\":\"2147483648.0\"}]}",
                        MediaType.APPLICATION_JSON));
        assertEquals(
                new RegistryLookup.Available<>(
                        new TagCatalogue(List.of("latest", "1.0", "1.0-alpine", "2147483648.0"))),
                adapter.listTags(IMAGE));
        server.verify();
    }

    @Test
    void malformedBodiesAreUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"count\":0}", MediaType.APPLICATION_JSON));
        assertInstanceOf(RegistryLookup.Unavailable.class, adapter.listTags(IMAGE));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "null",
                "not-json",
                "{\"results\":null}",
                "{\"results\":[],\"next\":42}",
                "{\"results\":[],\"next\":\" \"}"
            })
    void rejectsInvalidResponses(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertInstanceOf(RegistryLookup.Unavailable.class, adapter.listTags(IMAGE));
        server.verify();
    }

    @Test
    void first404IsMissingButLater404IsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withResourceNotFound());
        assertInstanceOf(RegistryLookup.Missing.class, adapter.listTags(IMAGE));
        server.verify();
        server.reset();
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"results\":[{\"name\":\"9.0\"}],\"next\":\"?page=2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://repositories/library/nginx/tags?page=2"))
                .andRespond(withResourceNotFound());
        assertInstanceOf(RegistryLookup.Unavailable.class, adapter.listTags(IMAGE));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"results\":[{}]}", "{\"results\":[{\"name\":null}]}", "{\"results\":[{\"name\":42}]}"})
    void malformedEntriesCannotBecomeSuccessfulEmptyCatalogue(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"results\":[{\"name\":\"3.0\"}]}", MediaType.APPLICATION_JSON));
        assertInstanceOf(RegistryLookup.Unavailable.class, adapter.listTags(IMAGE));
        assertEquals(new RegistryLookup.Available<>(new TagCatalogue(List.of("3.0"))), adapter.listTags(IMAGE));
        server.verify();
    }
}
