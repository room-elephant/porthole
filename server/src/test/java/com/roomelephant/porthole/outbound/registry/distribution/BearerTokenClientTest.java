package com.roomelephant.porthole.outbound.registry.distribution;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.roomelephant.porthole.core.model.ImageReference;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class BearerTokenClientTest {
    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final ImageReference IMAGE =
            ImageReference.tryParse("nginx:latest").orElseThrow();
    private final MutableClock clock = new MutableClock();
    private MockRestServiceServer server;
    private BearerTokenClient client;
    private RestClient http;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        http = builder.build();
        client = new BearerTokenClient(http, JsonMapper.builder().build(), "https://auth/", clock);
    }

    @ParameterizedTest
    @ValueSource(longs = {5, 60, 3600})
    void reusesTokensOnlyUntilTheirReportedExpiry(long lifetime) {
        token("first", ",\"expires_in\":" + lifetime);
        manifest("first");
        manifest("first");
        token("second", "");
        manifest("second");
        check();
        clock.advance(lifetime - 1);
        check();
        clock.advance(1);
        check();
        server.verify();
    }

    @Test
    void omittedLifetimeDefaultsToSixtySeconds() {
        token("first", "");
        manifest("first");
        token("second", "");
        manifest("second");
        check();
        clock.advance(60);
        check();
        server.verify();
    }

    @Test
    void expiryIsMeasuredFromTheReportedIssueTime() {
        token("first", ",\"issued_at\":\"2026-10-18T00:00:00Z\",\"expires_in\":10");
        manifest("first");
        token("second", "");
        manifest("second");
        check();
        clock.advance(5);
        check();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                ",\"expires_in\":-1",
                ",\"expires_in\":1.5",
                ",\"expires_in\":\"60\"",
                ",\"expires_in\":18446744073709551616",
                ",\"issued_at\":42",
                ",\"issued_at\":\"not-a-date\"",
                ",\"expires_in\":9223372036854775807"
            })
    void malformedExpiryCannotSupplyManifestEvidence(String fields) {
        token("first", fields);
        assertThatThrownBy(this::check).isInstanceOf(RegistryProtocolException.class);
        server.verify();
    }

    @Test
    void auth404IsNotAResource404() {
        server.expect(requestTo("https://auth/library/nginx:pull")).andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.execute(IMAGE, token -> {
                    throw new AssertionError("Must not execute");
                }))
                .isInstanceOf(RegistryProtocolException.class);
        server.verify();
    }

    @Test
    void staleRejectionCannotEvictReplacement() throws Exception {
        token("first", "");
        token("second", "");
        var acquired = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var firstCall = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var slow = executor.submit(() -> client.execute(IMAGE, value -> {
                if (firstCall.getAndSet(false)) {
                    acquired.countDown();
                    try {
                        if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                            throw new AssertionError("Timeout");
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                    throw unauthorized();
                }
                return value;
            }));
            try {
                assertThat(acquired.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isTrue();
                assertThat(client.<String>execute(IMAGE, value -> {
                            if (value.equals("first")) throw unauthorized();
                            return value;
                        }))
                        .isEqualTo("second");
            } finally {
                release.countDown();
            }
            assertThat(slow.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("second");
            assertThat(client.<String>execute(IMAGE, value -> value)).isEqualTo("second");
        }
        server.verify();
    }

    @Test
    void stopsAfterOne401Retry() {
        token("first", "");
        token("second", "");
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(() -> client.execute(IMAGE, value -> {
                    attempts.incrementAndGet();
                    throw unauthorized();
                }))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.Unauthorized.class);
        assertThat(attempts.get()).isEqualTo(2);
        server.verify();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {403, 404, 429})
    void otherStatusesAreNotRetried(int status) {
        token("first", "");
        assertThatThrownBy(() -> client.execute(IMAGE, value -> {
                    throw org.springframework.web.client.HttpClientErrorException.create(
                            HttpStatus.valueOf(status), "", HttpHeaders.EMPTY, new byte[0], null);
                }))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
        server.verify();
    }

    private static org.springframework.web.client.HttpClientErrorException unauthorized() {
        return org.springframework.web.client.HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "", HttpHeaders.EMPTY, new byte[0], null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", " ", "not-json", "[]", "{\"token\":42}", "{\"token\":\"\"}"})
    void malformedTokenPayloadIsAProtocolFailure(String body) {
        server.expect(requestTo("https://auth/library/nginx:pull"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.execute(IMAGE, value -> {
                    throw new AssertionError("Must not execute");
                }))
                .isInstanceOf(RegistryProtocolException.class);
        server.verify();
    }

    @Test
    void malformedAuthContentTypeIsAProtocolFailure() {
        server.expect(requestTo("https://auth/library/nginx:pull"))
                .andRespond(withSuccess().body("{\"token\":\"test\"}").header("Content-Type", "bad-type"));
        assertThatThrownBy(() -> client.execute(IMAGE, value -> value)).isInstanceOf(RegistryProtocolException.class);
        server.verify();
    }

    private void check() {
        client.execute(
                IMAGE,
                token -> http.head()
                        .uri("https://registry/library/nginx/manifests/latest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .toBodilessEntity());
    }

    private void token(String value, String fields) {
        server.expect(requestTo("https://auth/library/nginx:pull"))
                .andRespond(withSuccess("{\"token\":\"" + value + "\"" + fields + "}", MediaType.APPLICATION_JSON));
    }

    private void manifest(String value) {
        server.expect(requestTo("https://registry/library/nginx/manifests/latest"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + value))
                .andRespond(withSuccess("", MediaType.parseMediaType("application/vnd.oci.image.index.v1+json"))
                        .header("Docker-Content-Digest", DIGEST));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-18T00:00:05Z");

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }
    }
}
