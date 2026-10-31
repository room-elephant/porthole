package com.roomelephant.porthole.outbound.preferences;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.ContainerPreferencesValidation;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
    private static final String SELECTED_ENDPOINT = "selectedEndpoint";

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
        var previous = records.get(key);
        var preferences = previous == null
                ? mapper.createObjectNode()
                : previous.preferences().deepCopy();
        if (selection == null) {
            preferences.remove(SELECTED_ENDPOINT);
        } else {
            ObjectNode selected = mapper.valueToTree(selection);
            if (selection.url() == null) {
                selected.remove("url");
            }
            preferences.set(SELECTED_ENDPOINT, selected);
        }
        var updatedRecords = new LinkedHashMap<>(records);
        var updatedSnapshot = new LinkedHashMap<>(snapshot);
        var confirmed = new ContainerPreferences(selection);
        if (preferences.isEmpty()) {
            // Nothing left to remember: drop the record so automatic selections do not accumulate.
            updatedRecords.remove(key);
            updatedSnapshot.remove(key);
        } else {
            var record = new StoredContainerPreferences(key.sourceId(), key.containerName(), preferences);
            confirmed = preferencesOf(key, record);
            updatedRecords.put(key, record);
            updatedSnapshot.put(key, confirmed);
        }
        persist(updatedRecords);
        records = updatedRecords;
        snapshot = Map.copyOf(updatedSnapshot);
        return confirmed;
    }

    private Map<ContainerPreferencesKey, StoredContainerPreferences> load() throws IOException {
        byte[] contents;
        try {
            contents = Files.readAllBytes(path);
        } catch (NoSuchFileException _) {
            return Map.of();
        }
        if (new String(contents, StandardCharsets.UTF_8).isBlank()) {
            // A pre-created (touch) or never-written file holds no preferences.
            return Map.of();
        }
        var stored = mapper.readValue(contents, StoredContainerPreferences[].class);
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
        var node = record.preferences().get(SELECTED_ENDPOINT);
        SelectedEndpoint selection =
                node == null || node.isNull() ? null : mapper.treeToValue(node, SelectedEndpoint.class);
        ContainerPreferencesValidation.validate(key, selection);
        return new ContainerPreferences(selection);
    }

    private void persist(Map<ContainerPreferencesKey, StoredContainerPreferences> updated) {
        Path temporary = null;
        try {
            var bytes = mapper.writeValueAsBytes(updated.values());
            var parent = Objects.requireNonNull(path.getParent());
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".preferences-", ".tmp");
            writeDurably(temporary, bytes);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (FileSystemException moveFailure) {
                // rename(2) cannot replace a file that is itself a bind mount (EBUSY); overwrite it in place instead.
                if (!Files.isRegularFile(path)) {
                    throw moveFailure;
                }
                writeDurably(path, bytes);
                Files.deleteIfExists(temporary);
            }
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

    /** Writes and syncs to disk so a crash after the rename cannot leave an empty or truncated file behind. */
    private static void writeDurably(Path target, byte[] bytes) throws IOException {
        try (var channel = FileChannel.open(
                target, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }
}
