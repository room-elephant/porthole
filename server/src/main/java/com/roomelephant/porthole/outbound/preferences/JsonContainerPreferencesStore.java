package com.roomelephant.porthole.outbound.preferences;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.ContainerPreferencesValidation;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@NullMarked
public final class JsonContainerPreferencesStore implements ContainerPreferencesStore {
    private final Path path;
    private final JsonMapper mapper;
    private Map<ContainerPreferencesKey, StoredContainerPreferences> records;
    private volatile Map<ContainerPreferencesKey, ContainerPreferences> snapshot;

    public JsonContainerPreferencesStore(Path path, JsonMapper mapper) {
        this.path = path.toAbsolutePath().normalize();
        this.mapper = mapper;
        try {
            records = load();
            snapshot = snapshotOf(records);
        } catch (IOException | JacksonException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to load container preferences from " + this.path, e);
        }
    }

    @Override
    public Map<ContainerPreferencesKey, ContainerPreferences> snapshot() {
        return snapshot;
    }

    @Override
    public synchronized ContainerPreferences replaceSelection(
            ContainerPreferencesKey key, @Nullable SelectedEndpoint selection) {
        var updated = new LinkedHashMap<>(records);
        var previous = records.get(key);
        var preferences = previous == null
                ? mapper.createObjectNode()
                : previous.preferences().deepCopy();
        if (selection == null) {
            preferences.putNull("selectedEndpoint");
        } else {
            ObjectNode selected = mapper.valueToTree(selection);
            if (selection.url() == null) {
                selected.remove("url");
            }
            preferences.set("selectedEndpoint", selected);
        }
        updated.put(key, new StoredContainerPreferences(key.sourceId(), key.containerName(), preferences));
        var updatedSnapshot = snapshotOf(updated);
        persist(updated);
        records = updated;
        snapshot = updatedSnapshot;
        return updatedSnapshot.get(key);
    }

    private Map<ContainerPreferencesKey, StoredContainerPreferences> load() throws IOException {
        StoredContainerPreferences[] stored;
        try (var input = Files.newInputStream(path)) {
            stored = mapper.readValue(input, StoredContainerPreferences[].class);
        } catch (NoSuchFileException _) {
            return Map.of();
        }
        if (stored == null) {
            throw new IllegalArgumentException("Expected an array of container preference records");
        }
        var loaded = new LinkedHashMap<ContainerPreferencesKey, StoredContainerPreferences>();
        for (var record : stored) {
            if (record == null
                    || record.sourceId() == null
                    || record.containerName() == null
                    || record.preferences() == null) {
                throw new IllegalArgumentException("Incomplete container preference record");
            }
            var key = new ContainerPreferencesKey(record.sourceId(), record.containerName());
            preferencesOf(key, record);
            loaded.put(key, record);
        }
        return loaded;
    }

    private Map<ContainerPreferencesKey, ContainerPreferences> snapshotOf(
            Map<ContainerPreferencesKey, StoredContainerPreferences> stored) {
        var values = new LinkedHashMap<ContainerPreferencesKey, ContainerPreferences>();
        stored.forEach((key, record) -> values.put(key, preferencesOf(key, record)));
        return Map.copyOf(values);
    }

    private ContainerPreferences preferencesOf(ContainerPreferencesKey key, StoredContainerPreferences record) {
        var node = record.preferences().get("selectedEndpoint");
        SelectedEndpoint selection =
                node == null || node.isNull() ? null : mapper.treeToValue(node, SelectedEndpoint.class);
        ContainerPreferencesValidation.validate(key, selection);
        return new ContainerPreferences(selection);
    }

    private void persist(Map<ContainerPreferencesKey, StoredContainerPreferences> updated) {
        Path temporary = null;
        try {
            var parent = Objects.requireNonNull(path.getParent());
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".preferences-", ".tmp");
            Files.write(temporary, mapper.writeValueAsBytes(updated.values()));
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | JacksonException e) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            throw new IllegalStateException("Failed to save container preferences to " + path, e);
        }
    }
}
