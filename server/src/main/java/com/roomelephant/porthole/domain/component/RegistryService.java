package com.roomelephant.porthole.domain.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.domain.util.ImageUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class RegistryService {

    private static final String BEARER = "Bearer ";
    private static final String TOKEN = "token";
    private static final String ACCEPT_HEADER = "application/vnd.docker.distribution.manifest.v2+json";
    private static final String DOCKER_CONTENT_DIGEST = "Docker-Content-Digest";
    private static final String RESULTS = "results";
    private static final String NAME = "name";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RegistryProperties registryProperties;
    private final Cache<String, String> versionCache;
    private final Cache<String, Optional<String>> tokenCache;

    public RegistryService(RestClient restClient, RegistryProperties registryProperties) {
        this.restClient = restClient;
        this.registryProperties = registryProperties;
        this.objectMapper = new ObjectMapper();
        this.versionCache = Caffeine.newBuilder()
                .expireAfterWrite(registryProperties.cache().ttl())
                .maximumSize(registryProperties.cache().versionMaxSize())
                .build();
        this.tokenCache = Caffeine.newBuilder()
                .expireAfterWrite(registryProperties.cache().ttl())
                .maximumSize(1)
                .build();
    }

    public @Nullable String getDigest(@NonNull String imageName, String tag) {
        try {
            String repository = ImageUtils.resolveRepository(imageName);
            String token = getAuthToken(repository);

            if (token == null) return null;

            return fetchDigest(tag, repository, token);
        } catch (Exception e) {
            log.debug("Could not fetch digest for {}:{} - {}", imageName, tag, e.getMessage());
            return null;
        }
    }

    public @Nullable String getLatestVersion(@NonNull String imageName) {
        try {
            return versionCache.get(imageName, this::fetchLatestVersion);
        } catch (Exception e) {
            log.error("Could not fetch tags for {}", imageName, e);
            return null;
        }
    }

    private @Nullable String fetchLatestVersion(String imageName) {
        String repository = ImageUtils.resolveRepository(imageName);
        return fetchLatestFromHub(repository);
    }

    private @Nullable String fetchDigest(String tag, String repository, String token) {
        String url = registryProperties.urls().registry() + repository + "/manifests/" + tag;

        var response = restClient
                .head()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, BEARER + token)
                .header(HttpHeaders.ACCEPT, ACCEPT_HEADER)
                .retrieve()
                .toBodilessEntity();

        return response.getHeaders().getFirst(DOCKER_CONTENT_DIGEST);
    }

    private @Nullable String getAuthToken(String repository) {
        return tokenCache.get(repository, this::fetchAuthToken).orElse(null);
    }

    private Optional<String> fetchAuthToken(String repository) {
        String url = registryProperties.urls().auth() + repository + ":pull";
        try {
            String responseBody = restClient.get().uri(url).retrieve().body(String.class);
            if (responseBody == null) {
                return Optional.empty();
            }
            JsonNode response = objectMapper.readTree(responseBody);
            boolean hasToken = response.has(TOKEN);
            return hasToken ? Optional.of(response.get(TOKEN).asText()) : Optional.empty();
        } catch (Exception e) {
            log.error("Could not fetch auth token for {} at URL {}", repository, url, e);
            return Optional.empty();
        }
    }

    private @Nullable String fetchLatestFromHub(String repository) {
        String url = registryProperties.urls().repositories() + repository + "/tags?page_size=100";
        try {
            String responseBody = restClient.get().uri(url).retrieve().body(String.class);

            if (responseBody == null) return null;
            JsonNode response = objectMapper.readTree(responseBody);
            if (!response.has(RESULTS)) {
                return null;
            }

            List<String> tags = new ArrayList<>();
            for (JsonNode result : response.get(RESULTS)) {
                String name = result.get(NAME).asText();
                if (ImageUtils.isSemver(name)) {
                    tags.add(name);
                }
            }

            tags.sort(ImageUtils::compareSemVer);
            if (tags.isEmpty()) {
                return null;
            }

            return tags.getLast();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.error("Error in fetchLatestFromHub", e);
            return null;
        }
    }
}
