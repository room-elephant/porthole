package com.roomelephant.porthole.outbound.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.roomelephant.porthole.config.properties.GhcrProperties;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.ImageReference;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
public class HttpRegistryOperations implements com.roomelephant.porthole.core.port.outbound.RegistryOperations {

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
    private final Cache<String, String> tokenCache;

    public HttpRegistryOperations(
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

            return requestHead(tag, reference.get(), token).getHeaders().getFirst(DOCKER_CONTENT_DIGEST);
        } catch (Exception e) {
            log.debug("Could not fetch digest for {}:{} - {}", imageName, tag, e.getMessage());
            return null;
        }
    }

    public void invalidateVersionCache() {
        versionCache.invalidateAll();
    }

    public @Nullable String getLatestVersion(@NonNull String imageName) {
        var reference = ImageReference.tryParse(imageName).filter(ImageReference::supportsUpdates);
        if (reference.isEmpty()) return null;
        return versionCache.get(reference.get().canonicalRepository(), key -> fetchLatest(reference.get()));
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
        String token = getAuthToken(image.get());
        try {
            var remote = requestHead(image.get().lookupTag(), image.get(), token);
            String remoteDigest = remote.getHeaders().getFirst(DOCKER_CONTENT_DIGEST);
            int remoteLevel = manifestLevel(remote);
            if (!validDigest(remoteDigest) || remoteLevel == 0 || localDigests.contains(remoteDigest)) return false;
            for (String digest : localDigests) {
                if (!validDigest(digest)
                        || !digest.split(":", 2)[0].equals(remoteDigest.split(":", 2)[0])) continue;
                if (hasManifestAtLevel(digest, image.get(), token, remoteLevel)) return true;
            }
        } catch (HttpClientErrorException.NotFound _) {
            // Missing remote metadata is not evidence of a changed image.
        }
        return false;
    }

    private boolean hasManifestAtLevel(String digest, ImageReference image, String token, int level) {
        try {
            var local = requestHead(digest, image, token);
            return digest.equals(local.getHeaders().getFirst(DOCKER_CONTENT_DIGEST)) && manifestLevel(local) == level;
        } catch (HttpClientErrorException.NotFound _) {
            // A manifest removed by the registry supplies no comparison evidence.
            return false;
        }
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

    private String getAuthToken(ImageReference image) {
        // GHCR anonymous pull tokens are short-lived; obtain one per operation.
        if (isGhcr(image)) return fetchAuthToken(image);
        return tokenCache.get(image.canonicalRepository(), key -> fetchAuthToken(image));
    }

    private static boolean isGhcr(ImageReference image) {
        return image.registry().equals("ghcr.io");
    }

    private String registryBase(ImageReference image) {
        return isGhcr(image)
                ? ghcrProperties.registryUrl()
                : registryProperties.dockerhub().registryUrl();
    }

    private String fetchAuthToken(ImageReference image) {
        String auth = isGhcr(image)
                ? ghcrProperties.authUrl()
                : registryProperties.dockerhub().authUrl();
        String url = auth + image.repository() + ":pull";
        try {
            String responseBody = restClient.get().uri(url).retrieve().body(String.class);
            if (responseBody == null) {
                throw new IllegalStateException("Missing registry token response");
            }
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode token = response.has(TOKEN) ? response.get(TOKEN) : response.path("access_token");
            if (!token.isTextual() || token.asText().isBlank()) {
                throw new IllegalStateException("Missing anonymous registry token");
            }
            return token.asText();
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid registry token response", e);
        }
    }

    private @Nullable String fetchLatest(ImageReference image) {
        String token = isGhcr(image) ? getAuthToken(image) : null;
        URI first = URI.create(
                isGhcr(image)
                        ? registryBase(image) + image.repository() + "/tags/list?n=100"
                        : registryProperties.dockerhub().repositoriesUrl() + image.repository()
                                + "/tags?page_size=100");
        URI next = first;
        var visited = new HashSet<URI>();
        String best = null;
        long started = System.nanoTime();
        long budget = registryProperties.timeout().read().toNanos();
        try {
            for (int page = 0; page < 100; page++) {
                if (!visited.add(next) || !RegistryTagPage.sameCatalogue(first, next)) {
                    throw new IllegalStateException("Incomplete or unsafe registry catalogue");
                }
                RegistryTagPage result = fetchPage(image, next, token);
                if (System.nanoTime() - started > budget) {
                    throw new IllegalStateException("Registry catalogue exceeded the read budget");
                }
                best = RegistryTagPage.higherTag(best, result.best());
                URI following = result.next();
                if (following == null) return best;
                next = following;
            }
        } catch (HttpClientErrorException.NotFound e) {
            if (next.equals(first)) return null;
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid registry tag response", e);
        }
        throw new IllegalStateException("Registry catalogue exceeded the page limit");
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
