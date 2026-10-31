package com.roomelephant.porthole.core.model;

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
    private String displayName;
    private final String image;
    private final Details details;
    private String iconUrl = "";
    private List<Endpoint> urlCandidates;
    private ContainerPreferences preferences = new ContainerPreferences(null);
    private @Nullable VersionInfo version;
    private boolean self;

    public record Details(
            Set<Integer> publishedPorts,
            Map<String, String> labels,
            @Nullable String project,
            @Nullable String state,
            @Nullable String status) {
        public Details {
            publishedPorts = Set.copyOf(publishedPorts);
            labels = Map.copyOf(labels);
        }
    }

    public Container(
            String id,
            String sourceId,
            String name,
            String displayName,
            String image,
            Details details,
            List<Endpoint> urlCandidates) {
        this.id = id;
        this.sourceId = sourceId;
        this.name = name;
        this.displayName = displayName;
        this.image = image;
        this.details = details;
        this.urlCandidates = List.copyOf(urlCandidates);
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

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String image() {
        return image;
    }

    public Set<Integer> publishedPorts() {
        return details.publishedPorts();
    }

    public Map<String, String> labels() {
        return details.labels();
    }

    public List<Endpoint> urlCandidates() {
        return urlCandidates;
    }

    public boolean hasWebUi() {
        SelectedEndpoint selection = preferences.selectedEndpoint();
        return !urlCandidates.isEmpty() && (selection == null || !selection.selectsNone());
    }

    public ContainerPreferences preferences() {
        return preferences;
    }

    public void setPreferences(ContainerPreferences preferences) {
        this.preferences = preferences;
    }

    public @Nullable VersionInfo version() {
        return version;
    }

    public void setVersion(@Nullable VersionInfo version) {
        this.version = version;
    }

    public boolean isSelf() {
        return self;
    }

    public void setSelf(boolean self) {
        this.self = self;
    }

    public String iconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public @Nullable String project() {
        return details.project();
    }

    public @Nullable String state() {
        return details.state();
    }

    public @Nullable String status() {
        return details.status();
    }

    public void setUrlCandidates(List<Endpoint> candidates) {
        this.urlCandidates = List.copyOf(candidates);
    }
}
