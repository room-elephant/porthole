package com.roomelephant.porthole.domain.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.domain.model.ImageReference;
import com.roomelephant.porthole.domain.util.ImageUtils;
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
        var reference = ImageReference.tryParse(imageName).filter(ImageReference::supportsUpdates);
        if (reference.isEmpty() || !tag.equals(reference.get().lookupTag())) return null;
        try {
            String repository = reference.get().repository();
            String token = getAuthToken(repository);

            if (token == null) return null;

            return fetchDigest(tag, repository, token);
        } catch (Exception e) {
            log.debug("Could not fetch digest for {}:{} - {}", imageName, tag, e.getMessage());
            return null;
        }
    }

    public @Nullable String getLatestVersion(@NonNull String imageName) {
        if (ImageReference.tryParse(imageName)
                .filter(ImageReference::supportsUpdates)
                .isEmpty()) return null;
        try {
            return versionCache.get(ImageUtils.resolveRepository(imageName), this::fetchLatestFromHub);
        } catch (Exception e) {
            log.error("Could not fetch tags for {}", imageName, e);
            return null;
        }
    }

    private ResponseEntity<Void> requestHead(String reference, String repository, String token) {
        return restClient
                .head()
                .uri(registryProperties.urls().registry() + repository + "/manifests/" + reference)
                .header(HttpHeaders.AUTHORIZATION, BEARER + token)
                .header(HttpHeaders.ACCEPT, ACCEPT_HEADER)
                .retrieve()
                .toBodilessEntity();
    }

    private @Nullable String fetchDigest(String tag, String repository, String token) {
        return requestHead(tag, repository, token).getHeaders().getFirst(DOCKER_CONTENT_DIGEST);
    }

    /** Unknown or incompatible manifest levels are not evidence of an image change. */
    public boolean isTagChanged(String imageName, List<String> localDigests) {
        var image = ImageReference.tryParse(imageName).filter(ImageReference::supportsUpdates);
        if (image.isEmpty() || localDigests.isEmpty()) return false;
        try {
            String repository = image.get().repository();
            String token = getAuthToken(repository);
            if (token == null) return false;
            var remote = requestHead(image.get().lookupTag(), repository, token);
            String remoteDigest = remote.getHeaders().getFirst(DOCKER_CONTENT_DIGEST);
            int remoteLevel = manifestLevel(remote);
            if (!validDigest(remoteDigest) || remoteLevel == 0 || localDigests.contains(remoteDigest)) return false;
            for (String digest : localDigests) {
                if (!validDigest(digest)
                        || !digest.split(":", 2)[0].equals(remoteDigest.split(":", 2)[0])) continue;
                try {
                    var local = requestHead(digest, repository, token);
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
        URI first = URI.create(registryProperties.urls().repositories() + repository + "/tags?page_size=100");
        URI next = first;
        var visited = new HashSet<URI>();
        String best = null;
        long started = System.nanoTime();
        long budget = registryProperties.timeout().read().toNanos();
        try {
            for (int page = 0; page < 100; page++) {
                if (!visited.add(next) || !sameCatalogue(first, next)) return null;
                String body = restClient.get().uri(next.toString()).retrieve().body(String.class);
                if (body == null || System.nanoTime() - started > budget) return null;
                TagPage result = parsePage(body, next);
                best = higherTag(best, result.best());
                if (result.next() == null) return best;
                next = result.next();
            }
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.debug("Could not enumerate tags for {}: {}", repository, e.getMessage());
        }
        return null;
    }

    private TagPage parsePage(String body, URI current) throws IOException {
        JsonNode response = objectMapper.readTree(body);
        if (!response.path(RESULTS).isArray()) throw new IllegalArgumentException("Missing tag results");
        String best = bestTag(response.get(RESULTS));
        JsonNode link = response.get("next");
        if (link == null || link.isNull()) return new TagPage(best, null);
        if (!link.isTextual() || link.asText().isBlank()) throw new IllegalArgumentException("Invalid next page");
        String target = link.asText();
        // URI.resolve treats a query-only reference as a sibling path, losing "tags".
        URI next = target.startsWith("?")
                ? URI.create(current.toString().split("\\?", 2)[0] + target)
                : current.resolve(target);
        return new TagPage(best, next);
    }

    private static @Nullable String bestTag(JsonNode results) {
        String best = null;
        for (JsonNode result : results) {
            JsonNode name = result.get(NAME);
            if (name != null && name.isTextual() && ImageUtils.isSemver(name.asText()))
                best = higherTag(best, name.asText());
        }
        return best;
    }

    private static @Nullable String higherTag(@Nullable String left, @Nullable String right) {
        if (left == null) return right;
        return right != null && compareTags(right, left) > 0 ? right : left;
    }

    private record TagPage(@Nullable String best, @Nullable URI next) {}

    private static int compareTags(String left, String right) {
        int comparison = ImageUtils.compareSemVer(left, right);
        return comparison == 0 ? left.compareTo(right) : comparison;
    }

    private static boolean sameCatalogue(URI first, URI next) {
        return first.getScheme().equalsIgnoreCase(next.getScheme())
                && first.getRawAuthority().equalsIgnoreCase(next.getRawAuthority())
                && first.getRawPath().equals(next.getRawPath())
                && next.getFragment() == null
                && next.getUserInfo() == null;
    }
}
