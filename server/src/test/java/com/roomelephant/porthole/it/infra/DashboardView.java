package com.roomelephant.porthole.it.infra;

import com.roomelephant.porthole.versions.VersionInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /api/dashboard as integration tests read it. Filtering moved to the browser, so {@link #containers(boolean,
 * boolean)} applies the same rules the client does when a test needs the filtered view.
 */
public record DashboardView(List<SourceView> sources, List<ContainerView> containers, List<LinkView> links) {
    public static final String PATH = "/api/dashboard";
    private static final Set<String> STOPPED = Set.of("created", "exited", "dead", "removing");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static DashboardView parse(String json) {
        JsonNode root = MAPPER.readTree(json);
        List<SourceView> sources = new ArrayList<>();
        for (JsonNode source : root.path("sources")) {
            sources.add(new SourceView(
                    source.path("id").asString(),
                    source.path("name").asString(),
                    source.path("status").asString(),
                    text(source.path("error")),
                    text(source.path("lastSuccessAt"))));
        }
        List<ContainerView> containers = new ArrayList<>();
        List<LinkView> links = new ArrayList<>();
        for (JsonNode entry : root.path("entries")) {
            List<EndpointView> endpoints = new ArrayList<>();
            for (JsonNode endpoint : entry.path("endpoints")) {
                endpoints.add(new EndpointView(
                        endpoint.path("id").asString(),
                        endpoint.path("source").asString(),
                        endpoint.path("url").asString(),
                        endpoint.path("selected").asBoolean()));
            }
            String iconUrl = text(entry.path("icon").path("url"));
            if (entry.path("kind").asString().equals("link")) {
                links.add(new LinkView(
                        entry.path("id").asString(),
                        entry.path("name").asString(),
                        endpoints.getFirst().url(),
                        text(entry.path("stack")),
                        iconUrl));
                continue;
            }
            JsonNode container = entry.path("container");
            JsonNode version = container.path("version");
            containers.add(new ContainerView(
                    entry.path("id").asString(),
                    container.path("sourceId").asString(),
                    container.path("name").asString(),
                    entry.path("name").asString(),
                    container.path("defaultName").asString(),
                    container.path("image").asString(),
                    List.copyOf(endpoints),
                    iconUrl,
                    text(entry.path("stack")),
                    text(container.path("state")),
                    text(container.path("status")),
                    version.isObject() ? MAPPER.treeToValue(version, VersionInfo.class) : null,
                    container.path("self").asBoolean(),
                    entry.path("hasWebUi").asBoolean()));
        }
        return new DashboardView(List.copyOf(sources), List.copyOf(containers), List.copyOf(links));
    }

    /** The containers the browser shows with these settings. */
    public List<ContainerView> containers(boolean includeWithoutEndpoints, boolean includeStopped) {
        return containers.stream()
                .filter(container -> includeStopped || !STOPPED.contains(container.state()))
                .filter(container -> includeWithoutEndpoints || container.hasEndpoints())
                .toList();
    }

    private static @Nullable String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    /** A filtered container list, shaped like the old inventory response so assertions read the same. */
    public record Filtered(List<ContainerView> containers) {}

    public record SourceView(
            String id,
            String name,
            String status,
            @Nullable String error,
            @Nullable String lastSuccessAt) {}

    public record EndpointView(String id, String source, String url, boolean selected) {}

    public record LinkView(
            String id,
            String name,
            String url,
            @Nullable String stack,
            @Nullable String iconUrl) {}

    public record ContainerView(
            String entryId,
            String sourceId,
            String name,
            String displayName,
            String defaultName,
            String image,
            List<EndpointView> endpoints,
            @Nullable String iconUrl,
            @Nullable String project,
            @Nullable String state,
            @Nullable String status,
            @Nullable VersionInfo version,
            boolean self,
            boolean hasWebUi) {
        public boolean hasEndpoints() {
            return !endpoints.isEmpty();
        }
    }
}
