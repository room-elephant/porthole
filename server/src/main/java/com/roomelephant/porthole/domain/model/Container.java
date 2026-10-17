package com.roomelephant.porthole.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Container {
    private final String id;
    private final String sourceId;
    private final String name;
    private final String displayName;
    private final String image;
    private final Set<Integer> publishedPorts;
    private final Map<String, String> labels;
    private List<Endpoint> urlCandidates;
    private @Nullable VersionInfo version;
    private final String iconUrl;
    private final @Nullable String project;
    private final @Nullable String state;
    private final @Nullable String status;

    public Container(
            String id,
            String sourceId,
            String name,
            String displayName,
            String image,
            Set<Integer> publishedPorts,
            Map<String, String> labels,
            List<Endpoint> urlCandidates,
            String iconUrl,
            @Nullable String project,
            @Nullable String state,
            @Nullable String status) {
        this.id = id;
        this.sourceId = sourceId;
        this.name = name;
        this.displayName = displayName;
        this.image = image;
        this.publishedPorts = Set.copyOf(publishedPorts);
        this.labels = Map.copyOf(labels);
        this.urlCandidates = List.copyOf(urlCandidates);
        this.iconUrl = iconUrl;
        this.project = project;
        this.state = state;
        this.status = status;
    }

    public String sourceId() {
        return sourceId;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public String image() {
        return image;
    }

    public Set<Integer> publishedPorts() {
        return publishedPorts;
    }

    public Map<String, String> labels() {
        return labels;
    }

    public List<Endpoint> urlCandidates() {
        return urlCandidates;
    }

    public @Nullable VersionInfo version() {
        return version;
    }

    public void setVersion(@Nullable VersionInfo version) {
        this.version = version;
    }

    public String iconUrl() {
        return iconUrl;
    }

    public @Nullable String project() {
        return project;
    }

    public @Nullable String state() {
        return state;
    }

    public @Nullable String status() {
        return status;
    }

    public void setUrlCandidates(List<Endpoint> candidates) {
        this.urlCandidates = List.copyOf(candidates);
    }
}
