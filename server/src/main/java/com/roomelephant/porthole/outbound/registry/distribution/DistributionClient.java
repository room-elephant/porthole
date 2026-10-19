package com.roomelephant.porthole.outbound.registry.distribution;

import com.fasterxml.jackson.databind.*;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.outbound.registry.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.jspecify.annotations.*;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

@NullMarked
public final class DistributionClient {
    private static final Set<String> INDEX_TYPES = Set.of(
            "application/vnd.oci.image.index.v1+json", "application/vnd.docker.distribution.manifest.list.v2+json");
    private static final Set<String> IMAGE_TYPES = Set.of(
            "application/vnd.oci.image.manifest.v1+json", "application/vnd.docker.distribution.manifest.v2+json");
    private static final String ACCEPT = java.util.stream.Stream.concat(INDEX_TYPES.stream(), IMAGE_TYPES.stream())
            .sorted()
            .collect(java.util.stream.Collectors.joining(", "));
    private final RestClient http;
    private final ObjectMapper json;
    private final String registryUrl;
    private final BearerTokenClient tokens;

    public DistributionClient(RestClient http, ObjectMapper json, String registryUrl, BearerTokenClient tokens) {
        this.http = http;
        this.json = json;
        this.registryUrl = registryUrl;
        this.tokens = tokens;
    }

    public TagPage readTagPage(ImageReference image, URI page) {
        ResponseEntity<String> response = tokens.execute(
                image,
                token -> http.get()
                        .uri(page)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .toEntity(String.class));
        JsonNode body = decode(response.getBody());
        JsonNode values = body.path("tags");
        if (!values.isArray()) throw new RegistryProtocolException("Missing registry tag list");
        List<String> tags = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual()) throw new RegistryProtocolException("Invalid registry tag entry");
            tags.add(value.asText());
        }
        String next = LinkHeaderParser.next(response.getHeaders().getOrEmpty(HttpHeaders.LINK));
        return new TagPage(tags, next == null ? null : TagPagination.resolve(page, next));
    }

    public RegistryLookup<ManifestMetadata> readManifest(ImageReference image, String reference) {
        return RegistryRequests.read(() -> {
            ResponseEntity<Void> response = tokens.execute(
                    image,
                    token -> http.head()
                            .uri(registryUrl + image.repository() + "/manifests/" + reference)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header(HttpHeaders.ACCEPT, ACCEPT)
                            .retrieve()
                            .toBodilessEntity());
            String digest = response.getHeaders().getFirst("Docker-Content-Digest");
            if (digest == null || ImageReference.tryParse("image@" + digest).isEmpty())
                throw new RegistryProtocolException("Missing or invalid manifest digest");
            MediaType type = response.getHeaders().getContentType();
            String mime = type == null ? "" : type.getType() + "/" + type.getSubtype();
            ManifestMetadata.Kind kind = ManifestMetadata.Kind.UNKNOWN;
            if (INDEX_TYPES.contains(mime)) {
                kind = ManifestMetadata.Kind.INDEX;
            } else if (IMAGE_TYPES.contains(mime)) {
                kind = ManifestMetadata.Kind.IMAGE;
            }
            return new ManifestMetadata(digest, kind);
        });
    }

    private JsonNode decode(@Nullable String body) {
        if (body == null) throw new RegistryProtocolException("Missing registry response");
        try {
            JsonNode result = json.readTree(body);
            if (result == null) throw new RegistryProtocolException("Missing registry response");
            return result;
        } catch (IOException e) {
            throw new RegistryProtocolException("Invalid registry JSON", e);
        }
    }
}
