package com.roomelephant.porthole.outbound.registry.ghcr;

import com.fasterxml.jackson.databind.*;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.ImageReference;
import com.roomelephant.porthole.outbound.registry.*;
import java.io.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.*;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@NullMarked
public final class GhcrClient implements RegistryClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RegistryProperties.Ghcr properties;
    private final Duration readTimeout;
    private final DistributionManifestClient manifests;

    public GhcrClient(RestClient restClient, RegistryProperties.Ghcr properties, Duration readTimeout) {
        this.restClient = restClient;
        this.properties = properties;
        this.readTimeout = readTimeout;
        this.manifests = new DistributionManifestClient(restClient, properties.registryUrl());
    }

    @Override
    public boolean supports(ImageReference image) {
        return image.supportsUpdates() && image.registry().equals("ghcr.io");
    }

    @Override
    public @Nullable String getLatestVersion(ImageReference image) {
        String token = fetchAuthToken(image);
        URI first = URI.create(properties.registryUrl() + image.repository() + "/tags/list?n=100");
        return RegistryTagPage.latest(first, readTimeout, current -> {
            var response = restClient
                    .get()
                    .uri(current.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toEntity(String.class);
            if (response.getBody() == null) throw new IllegalArgumentException("Missing tag response");
            return RegistryTagPage.distribution(
                    objectMapper.readTree(response.getBody()),
                    response.getHeaders().getOrEmpty(HttpHeaders.LINK),
                    current);
        });
    }

    @Override
    public boolean isTagChanged(ImageReference image, List<String> localDigests) {
        // Anonymous GHCR tokens are short-lived; obtain a fresh one for each operation.
        String token = fetchAuthToken(image);
        return manifests.isTagChanged(image, localDigests, token);
    }

    private String fetchAuthToken(ImageReference image) {
        String url = properties.authUrl() + image.repository() + ":pull";
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            if (body == null) throw new IllegalStateException("Missing registry token response");
            JsonNode response = objectMapper.readTree(body);
            JsonNode token = response.has("token") ? response.get("token") : response.path("access_token");
            if (!token.isTextual() || token.asText().isBlank())
                throw new IllegalStateException("Missing anonymous registry token");
            return token.asText();
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid registry token response", e);
        }
    }
}
