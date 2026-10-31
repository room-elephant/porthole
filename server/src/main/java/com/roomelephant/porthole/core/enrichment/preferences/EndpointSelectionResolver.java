package com.roomelephant.porthole.core.enrichment.preferences;

import com.roomelephant.porthole.core.model.Endpoint;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class EndpointSelectionResolver {
    public List<Endpoint> resolve(List<Endpoint> discovered, @Nullable SelectedEndpoint selection) {
        List<Endpoint> resolved = new ArrayList<>();
        for (Endpoint endpoint : discovered) {
            resolved.add(new Endpoint(endpoint.id(), endpoint.source(), endpoint.url(), false));
        }
        if (selection != null && selection.selectsNone()) return List.copyOf(resolved);
        if (selection != null && selection.selectsCustom()) {
            resolved.add(
                    new Endpoint(selection.id(), selection.source(), Objects.requireNonNull(selection.url()), true));
            return List.copyOf(resolved);
        }
        int selectedIndex = discoveredSelection(discovered, selection);
        if (selectedIndex >= 0) {
            Endpoint endpoint = resolved.get(selectedIndex);
            resolved.set(selectedIndex, new Endpoint(endpoint.id(), endpoint.source(), endpoint.url(), true));
        }
        return List.copyOf(resolved);
    }

    private int discoveredSelection(List<Endpoint> discovered, @Nullable SelectedEndpoint selection) {
        if (selection != null) {
            for (int index = 0; index < discovered.size(); index++) {
                Endpoint candidate = discovered.get(index);
                if (candidate.id().equals(selection.id()) && candidate.source().equals(selection.source()))
                    return index;
            }
        }
        List<Integer> traefik = IntStream.range(0, discovered.size())
                .filter(index -> "traefik".equals(discovered.get(index).source()))
                .boxed()
                .toList();
        if (traefik.isEmpty()) return discovered.size() == 1 ? 0 : -1;
        if (traefik.size() == 1) return traefik.getFirst();
        List<Integer> roots = traefik.stream()
                .filter(index -> isRoot(discovered.get(index).url()))
                .toList();
        return roots.size() == 1 ? roots.getFirst() : -1;
    }

    private boolean isRoot(String url) {
        String path = URI.create(url).getRawPath();
        if (path.isEmpty()) return true;
        String[] segments = path.substring(1).split("/", -1);
        int depth = 0;
        for (int index = 0; index < segments.length - 1; index++) {
            switch (segments[index]) {
                case "." -> {}
                case ".." -> depth = Math.max(0, depth - 1);
                default -> depth++;
            }
        }
        return switch (segments[segments.length - 1]) {
            case "", "." -> depth == 0;
            case ".." -> depth <= 1;
            default -> false;
        };
    }
}
