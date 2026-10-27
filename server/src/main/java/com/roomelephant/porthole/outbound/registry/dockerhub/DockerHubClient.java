package com.roomelephant.porthole.outbound.registry.dockerhub;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.outbound.registry.*;
import com.roomelephant.porthole.outbound.registry.distribution.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.*;

@NullMarked
public final class DockerHubClient implements RegistryClient {
    private final RestClient http;
    private final ObjectMapper json;
    private final RegistryProperties.DockerHub properties;
    private final Duration readTimeout;
    private final DistributionClient distribution;

    public DockerHubClient(
            RestClient http,
            ObjectMapper json,
            RegistryProperties.DockerHub properties,
            Duration readTimeout,
            DistributionClient distribution) {
        this.http = http;
        this.json = json;
        this.properties = properties;
        this.readTimeout = readTimeout;
        this.distribution = distribution;
    }

    @Override
    public boolean supports(ImageReference image) {
        return image.isMutable() && image.registry().equals("docker.io");
    }

    @Override
    public RegistryLookup<TagCatalogue> listTags(ImageReference image) {
        URI first = URI.create(properties.repositoriesUrl() + image.repository() + "/tags?page_size=100");
        return RegistryRequests.read(() -> TagPagination.read(first, readTimeout, this::readPage));
    }

    private TagPage readPage(URI page) {
        String body = http.get().uri(page).retrieve().body(String.class);
        if (body == null) throw new RegistryProtocolException("Missing tag response");
        try {
            JsonNode response = json.readTree(body);
            if (response == null || !response.path("results").isArray())
                throw new RegistryProtocolException("Missing tag results");
            List<String> tags = new ArrayList<>();
            for (JsonNode result : response.path("results")) {
                JsonNode name = result.path("name");
                if (!name.isString()) throw new RegistryProtocolException("Invalid registry tag entry");
                tags.add(name.asString());
            }
            JsonNode next = response.get("next");
            if (next == null || next.isNull()) return new TagPage(tags, null);
            if (!next.isString() || next.asString().isBlank()) throw new RegistryProtocolException("Invalid next page");
            return new TagPage(tags, TagPagination.resolve(page, next.asString()));
        } catch (JacksonException e) {
            throw new RegistryProtocolException("Invalid tag response", e);
        }
    }

    @Override
    public RegistryLookup<ManifestMetadata> readManifest(ImageReference image, String reference) {
        return distribution.readManifest(image, reference);
    }
}
