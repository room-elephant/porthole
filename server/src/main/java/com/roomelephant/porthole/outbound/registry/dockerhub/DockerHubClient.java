package com.roomelephant.porthole.outbound.registry.dockerhub;

import com.fasterxml.jackson.databind.*;
import com.github.benmanes.caffeine.cache.*;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.ImageReference;
import com.roomelephant.porthole.outbound.registry.*;
import java.io.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.*;
import org.springframework.web.client.RestClient;

@NullMarked
public final class DockerHubClient implements RegistryClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RegistryProperties.DockerHub properties;
    private final Duration readTimeout;
    private final DistributionManifestClient manifests;
    private final Cache<String, String> tokens;

    public DockerHubClient(
            RestClient restClient,
            RegistryProperties.DockerHub properties,
            Duration readTimeout,
            RegistryProperties.Cache cache) {
        this.restClient = restClient;
        this.properties = properties;
        this.readTimeout = readTimeout;
        this.manifests = new DistributionManifestClient(restClient, properties.registryUrl());
        this.tokens = Caffeine.newBuilder()
                .expireAfterWrite(cache.ttl())
                .maximumSize(1)
                .build();
    }

    @Override
    public boolean supports(ImageReference image) {
        return image.supportsUpdates() && image.registry().equals("docker.io");
    }

    @Override
    public @Nullable String getLatestVersion(ImageReference image) {
        URI first = URI.create(properties.repositoriesUrl() + image.repository() + "/tags?page_size=100");
        return RegistryTagPage.latest(first, readTimeout, current -> {
            String body = restClient.get().uri(current.toString()).retrieve().body(String.class);
            if (body == null) throw new IllegalArgumentException("Missing tag response");
            return RegistryTagPage.hub(objectMapper.readTree(body), current);
        });
    }

    @Override
    public boolean isTagChanged(ImageReference image, List<String> localDigests) {
        String token = tokens.get(image.canonicalRepository(), key -> fetchAuthToken(image));
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
