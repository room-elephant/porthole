package com.roomelephant.porthole.outbound.registry.distribution;

import com.fasterxml.jackson.databind.*;
import com.github.benmanes.caffeine.cache.*;
import com.roomelephant.porthole.core.model.ImageReference;
import java.io.*;
import java.time.*;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.client.*;

/** Anonymous token state owned by one configured registry provider. */
@NullMarked
public final class BearerTokenClient {
    private static final int MAX_CACHED_TOKENS = 100;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String authUrl;
    private final Clock clock;
    private final Cache<String, Token> tokens =
            Caffeine.newBuilder().maximumSize(MAX_CACHED_TOKENS).build();

    public BearerTokenClient(RestClient http, ObjectMapper json, String authUrl, Clock clock) {
        this.restClient = http;
        this.objectMapper = json;
        this.authUrl = authUrl;
        this.clock = clock;
    }

    public <T> T execute(ImageReference image, Function<String, T> request) {
        Token token = authToken(image);
        try {
            return request(image, token, request);
        } catch (HttpClientErrorException.Unauthorized _) {
            return request(image, authToken(image), request);
        }
    }

    private <T> T request(ImageReference image, Token token, Function<String, T> request) {
        try {
            return request.apply(token.value());
        } catch (HttpClientErrorException.Unauthorized e) {
            tokens.asMap().remove(image.canonicalRepository(), token);
            throw e;
        }
    }

    private Token authToken(ImageReference image) {
        return tokens.asMap()
                .compute(
                        image.canonicalRepository(),
                        (key, cached) -> cached != null && clock.instant().isBefore(cached.expiresAt())
                                ? cached
                                : fetchAuthToken(image));
    }

    private Token fetchAuthToken(ImageReference image) {
        Instant requestedAt = clock.instant();
        String url = authUrl + image.repository() + ":pull";
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            if (body == null) throw new RegistryProtocolException("Missing registry token response");
            JsonNode response = objectMapper.readTree(body);
            JsonNode token = response.has("token") ? response.get("token") : response.path("access_token");
            if (!token.isTextual() || token.asText().isBlank())
                throw new RegistryProtocolException("Missing anonymous registry token");
            return new Token(token.asText(), expiresAt(response, requestedAt));
        } catch (org.springframework.web.client.RestClientException
                | org.springframework.http.InvalidMediaTypeException e) {
            throw new RegistryProtocolException("Registry authentication unavailable", e);
        } catch (IOException e) {
            throw new RegistryProtocolException("Invalid registry token response", e);
        }
    }

    private Instant expiresAt(JsonNode response, Instant requestedAt) {
        JsonNode lifetime = response.get("expires_in");
        if (lifetime != null
                && (!lifetime.isIntegralNumber() || !lifetime.canConvertToLong() || lifetime.longValue() < 0))
            throw new RegistryProtocolException("Invalid registry token lifetime");
        JsonNode issuedAt = response.get("issued_at");
        if (issuedAt != null && !issuedAt.isTextual())
            throw new RegistryProtocolException("Invalid registry token issue time");
        try {
            Instant issued = issuedAt == null ? requestedAt : Instant.parse(issuedAt.asText());
            return issued.plusSeconds(lifetime == null ? 60 : lifetime.longValue());
        } catch (DateTimeException | ArithmeticException e) {
            throw new RegistryProtocolException("Invalid registry token expiry", e);
        }
    }

    private record Token(String value, Instant expiresAt) {}
}
