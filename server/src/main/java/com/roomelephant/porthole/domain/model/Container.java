package com.roomelephant.porthole.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record Container(
        String id,
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
    public Container {
        publishedPorts = Set.copyOf(publishedPorts);
        labels = Map.copyOf(labels);
        urlCandidates = List.copyOf(urlCandidates);
    }

    public Container withUrlCandidates(List<Endpoint> candidates) {
        return new Container(
                id, name, displayName, image, publishedPorts, labels, candidates, iconUrl, project, state, status);
    }
}
