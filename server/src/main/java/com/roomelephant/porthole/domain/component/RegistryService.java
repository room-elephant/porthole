package com.roomelephant.porthole.domain.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.roomelephant.porthole.config.properties.GhcrProperties;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.domain.model.ImageReference;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class RegistryService {

    private static final String BEARER = "Bearer ";
    private static final String TOKEN = "token";
    private static final Set<String> INDEX_TYPES = Set.of(
            "application/vnd.oci.image.index.v1+json", "application/vnd.docker.distribution.manifest.list.v2+json");
    private static final Set<String> IMAGE_TYPES = Set.of(
            "application/vnd.oci.image.manifest.v1+json", "application/vnd.docker.distribution.manifest.v2+json");
    private static final String ACCEPT_HEADER =
            Stream.concat(INDEX_TYPES.stream(), IMAGE_TYPES.stream()).sorted().collect(Collectors.joining(", "));
    private static final String DOCKER_CONTENT_DIGEST = "Docker-Content-Digest";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RegistryProperties registryProperties;
    private final GhcrProperties ghcrProperties;
    private final Cache<String, String> versionCache;
    private final Cache<String, Optional<String>> tokenCache;

    public RegistryService(
            RestClient restClient, RegistryProperties registryProperties, GhcrProperties ghcrProperties) {
        this.restClient = restClient;
        this.registryProperties = registryProperties;
        this.ghcrProperties = ghcrProperties;
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
        var reference = ImageReference.tryParse(imageName).filter(ImageReference::supportsUpdates);
        if (reference.isEmpty() || !tag.equals(reference.get().lookupTag())) return null;
        try {
            String token = getAuthToken(reference.get());

            if (token == null) return null;

            return requestHead(tag, reference.get(), token).getHeaders().getFirst(DOCKER_CONTENT_DIGEST);
        } catch (Exception e) {
            log.debug("Could not fetch digest for {}:{} - {}", imageName, tag, e.getMessage());
            return null;
        }
    }

    public @Nullable String getLatestVersion(@NonNull String imageName) {
        var reference = ImageReference.tryParse(imageName).filter(ImageReference::supportsUpdates);
        if (reference.isEmpty()) return null;
        try {
            return versionCache.get(reference.get().canonicalRepository(), key -> fetchLatest(reference.get()));
        } catch (Exception e) {
            log.error("Could not fetch tags for {}", imageName, e);
            return null;
        }
    }

    private ResponseEntity<Void> requestHead(String reference, ImageReference image, String token) {
        return restClient
                .head()
                .uri(registryBase(image) + image.repository() + "/manifests/" + reference)
                .header(HttpHeaders.AUTHORIZATION, BEARER + token)
                .header(HttpHeaders.ACCEPT, ACCEPT_HEADER)
                .retrieve()
                .toBodilessEntity();
    }

    /** Unknown or incompatible manifest levels are not evidence of an image change. */
    public boolean isTagChanged(String imageName, List<String> localDigests) {
        var image = ImageReference.tryParse(imageName).filter(ImageReference::supportsUpdates);
        if (image.isEmpty() || localDigests.isEmpty()) return false;
        try {
            String token = getAuthToken(image.get());
            if (token == null) return false;
            var remote = requestHead(image.get().lookupTag(), image.get(), token);
            String remoteDigest = remote.getHeaders().getFirst(DOCKER_CONTENT_DIGEST);
            int remoteLevel = manifestLevel(remote);
            if (!validDigest(remoteDigest) || remoteLevel == 0 || localDigests.contains(remoteDigest)) return false;
            for (String digest : localDigests) {
                if (!validDigest(digest)
                        || !digest.split(":", 2)[0].equals(remoteDigest.split(":", 2)[0])) continue;
                try {
                    var local = requestHead(digest, image.get(), token);
                    if (digest.equals(local.getHeaders().getFirst(DOCKER_CONTENT_DIGEST))
                            && manifestLevel(local) == remoteLevel) return true;
                } catch (Exception e) {
                    log.debug("Could not inspect local manifest {} for {}: {}", digest, imageName, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Could not compare manifests for {}: {}", imageName, e.getMessage());
        }
        return false;
    }

    private static boolean validDigest(@Nullable String digest) {
        return digest != null && ImageReference.tryParse("nginx@" + digest).isPresent();
    }

    private static int manifestLevel(ResponseEntity<Void> response) {
        var type = response.getHeaders().getContentType();
        if (type == null) return 0;
        String mime = type.getType() + "/" + type.getSubtype();
        if (INDEX_TYPES.contains(mime)) return 2;
        return IMAGE_TYPES.contains(mime) ? 1 : 0;
    }

    private @Nullable String getAuthToken(ImageReference image) {
        // GHCR anonymous pull tokens are short-lived; obtain one per operation.
        if (isGhcr(image)) return fetchAuthToken(image).orElse(null);
        return tokenCache
                .get(image.canonicalRepository(), key -> fetchAuthToken(image))
                .orElse(null);
    }

    private static boolean isGhcr(ImageReference image) {
        return image.registry().equals("ghcr.io");
    }

    private String registryBase(ImageReference image) {
        return isGhcr(image)
                ? ghcrProperties.registryUrl()
                : registryProperties.urls().registry();
    }

    private Optional<String> fetchAuthToken(ImageReference image) {
        String auth = isGhcr(image)
                ? ghcrProperties.authUrl()
                : registryProperties.urls().auth();
        String url = auth + image.repository() + ":pull";
        try {
            String responseBody = restClient.get().uri(url).retrieve().body(String.class);
            if (responseBody == null) {
                return Optional.empty();
            }
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode token = response.has(TOKEN) ? response.get(TOKEN) : response.path("access_token");
            return token.isTextual() && !token.asText().isBlank() ? Optional.of(token.asText()) : Optional.empty();
        } catch (Exception e) {
            log.debug("Could not fetch anonymous pull token for {}: {}", image.canonicalRepository(), e.getMessage());
            return Optional.empty();
        }
    }

    private @Nullable String fetchLatest(ImageReference image) {
        String token = isGhcr(image) ? getAuthToken(image) : null;
        if (isGhcr(image) && token == null) return null;
        URI first = URI.create(
                isGhcr(image)
                        ? registryBase(image) + image.repository() + "/tags/list?n=100"
                        : registryProperties.urls().repositories() + image.repository() + "/tags?page_size=100");
        URI next = first;
        var visited = new HashSet<URI>();
        String best = null;
        long started = System.nanoTime();
        long budget = registryProperties.timeout().read().toNanos();
        try {
            for (int page = 0; page < 100; page++) {
                if (!visited.add(next) || !RegistryTagPage.sameCatalogue(first, next)) return null;
                RegistryTagPage result = fetchPage(image, next, token);
                if (System.nanoTime() - started > budget) return null;
                best = RegistryTagPage.higherTag(best, result.best());
                if (result.next() == null) return best;
                next = result.next();
            }
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.debug("Could not enumerate tags for {}: {}", image.canonicalRepository(), e.getMessage());
        }
        return null;
    }

    private RegistryTagPage fetchPage(ImageReference image, URI current, @Nullable String token) throws IOException {
        var request = restClient.get().uri(current.toString());
        if (isGhcr(image)) {
            var response = request.header(HttpHeaders.AUTHORIZATION, BEARER + token)
                    .retrieve()
                    .toEntity(String.class);
            if (response.getBody() == null) throw new IllegalArgumentException("Missing tag response");
            return RegistryTagPage.distribution(
                    objectMapper.readTree(response.getBody()),
                    response.getHeaders().getOrEmpty(HttpHeaders.LINK),
                    current);
        }
        String body = request.retrieve().body(String.class);
        if (body == null) throw new IllegalArgumentException("Missing tag response");
        return RegistryTagPage.hub(objectMapper.readTree(body), current);
    }
}
