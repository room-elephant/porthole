package com.roomelephant.porthole.outbound.registry;

import com.roomelephant.porthole.core.model.ImageReference;
import java.util.*;
import java.util.stream.*;
import org.jspecify.annotations.*;
import org.springframework.http.*;
import org.springframework.web.client.*;

/** Shared Distribution manifest protocol; callers own authentication and token lifetime. */
@NullMarked
public final class DistributionManifestClient {
    private static final Set<String> INDEX_TYPES = Set.of(
            "application/vnd.oci.image.index.v1+json", "application/vnd.docker.distribution.manifest.list.v2+json");
    private static final Set<String> IMAGE_TYPES = Set.of(
            "application/vnd.oci.image.manifest.v1+json", "application/vnd.docker.distribution.manifest.v2+json");
    private static final String ACCEPT_HEADER =
            Stream.concat(INDEX_TYPES.stream(), IMAGE_TYPES.stream()).sorted().collect(Collectors.joining(", "));
    private static final String DOCKER_CONTENT_DIGEST = "Docker-Content-Digest";

    private final RestClient restClient;
    private final String registryUrl;

    public DistributionManifestClient(RestClient restClient, String registryUrl) {
        this.restClient = restClient;
        this.registryUrl = registryUrl;
    }

    /** Unknown or incompatible manifest levels are not evidence of an image change. */
    public boolean isTagChanged(ImageReference image, List<String> localDigests, String token) {
        try {
            ResponseEntity<Void> remote = requestHead(image.lookupTag(), image, token);
            String remoteDigest = remote.getHeaders().getFirst(DOCKER_CONTENT_DIGEST);
            int remoteLevel = manifestLevel(remote);
            if (!validDigest(remoteDigest) || remoteLevel == 0 || localDigests.contains(remoteDigest)) return false;
            for (String digest : localDigests) {
                if (!validDigest(digest)
                        || !digest.split(":", 2)[0].equals(remoteDigest.split(":", 2)[0])) continue;
                if (hasManifestAtLevel(digest, image, token, remoteLevel)) return true;
            }
        } catch (HttpClientErrorException.NotFound _) {
            // Missing remote metadata is not evidence of a changed image.
        }
        return false;
    }

    private boolean hasManifestAtLevel(String digest, ImageReference image, String token, int level) {
        try {
            ResponseEntity<Void> local = requestHead(digest, image, token);
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
        MediaType type = response.getHeaders().getContentType();
        if (type == null) return 0;
        String mime = type.getType() + "/" + type.getSubtype();
        if (INDEX_TYPES.contains(mime)) return 2;
        return IMAGE_TYPES.contains(mime) ? 1 : 0;
    }

    private ResponseEntity<Void> requestHead(String reference, ImageReference image, String token) {
        return restClient
                .head()
                .uri(registryUrl + image.repository() + "/manifests/" + reference)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, ACCEPT_HEADER)
                .retrieve()
                .toBodilessEntity();
    }
}
