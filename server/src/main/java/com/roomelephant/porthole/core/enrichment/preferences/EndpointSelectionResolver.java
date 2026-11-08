package com.roomelephant.porthole.core.enrichment.preferences;

import com.roomelephant.porthole.core.model.Endpoint;
import com.roomelephant.porthole.endpoints.Candidate;
import com.roomelephant.porthole.endpoints.CandidateKind;
import com.roomelephant.porthole.endpoints.EndpointSelection;
import com.roomelephant.porthole.entries.EndpointChoice;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class EndpointSelectionResolver {
    static final String CUSTOM = "custom";

    public List<Endpoint> resolve(List<Endpoint> discovered, @Nullable EndpointChoice selection) {
        List<Endpoint> resolved = new ArrayList<>();
        for (Endpoint endpoint : discovered) {
            resolved.add(new Endpoint(endpoint.id(), endpoint.source(), endpoint.url(), false));
        }
        if (selection instanceof EndpointChoice.None) return List.copyOf(resolved);
        if (selection instanceof EndpointChoice.Custom custom) {
            resolved.add(new Endpoint(CUSTOM, CUSTOM, custom.url(), true));
            return List.copyOf(resolved);
        }
        int selectedIndex = discoveredSelection(discovered, selection);
        if (selectedIndex >= 0) {
            Endpoint endpoint = resolved.get(selectedIndex);
            resolved.set(selectedIndex, new Endpoint(endpoint.id(), endpoint.source(), endpoint.url(), true));
        }
        return List.copyOf(resolved);
    }

    private int discoveredSelection(List<Endpoint> discovered, @Nullable EndpointChoice selection) {
        if (selection instanceof EndpointChoice.Discovered chosen) {
            for (int index = 0; index < discovered.size(); index++) {
                if (discovered.get(index).id().equals(chosen.id())) return index;
            }
        }
        List<Candidate> candidates = discovered.stream()
                .map(endpoint -> new Candidate(
                        endpoint.id(),
                        endpoint.source(),
                        endpoint.url(),
                        "traefik".equals(endpoint.source()) ? CandidateKind.ROUTED : CandidateKind.DIRECT))
                .toList();
        return EndpointSelection.automatic(candidates);
    }
}
