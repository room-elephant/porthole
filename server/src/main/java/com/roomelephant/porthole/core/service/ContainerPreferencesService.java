package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.ContainerPreferencesValidation;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.core.port.inbound.SelectContainerEndpoint;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@NullMarked
@Service
public class ContainerPreferencesService implements SelectContainerEndpoint {
    private final ContainerPreferencesStore store;
    private final DockerOperations sources;

    public ContainerPreferencesService(ContainerPreferencesStore store, DockerOperations sources) {
        this.store = store;
        this.sources = sources;
    }

    @Override
    public ContainerPreferences select(ContainerPreferencesKey key, @Nullable SelectedEndpoint selection) {
        ContainerPreferencesValidation.validate(key, selection);
        if (sources.sources().stream().noneMatch(source -> source.id().equals(key.sourceId()))) {
            throw new IllegalArgumentException("Unknown sourceId: " + key.sourceId());
        }
        return store.replaceSelection(key, selection);
    }
}
