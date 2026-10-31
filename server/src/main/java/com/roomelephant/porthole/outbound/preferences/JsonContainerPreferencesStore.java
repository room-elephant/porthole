package com.roomelephant.porthole.outbound.preferences;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
public final class JsonContainerPreferencesStore implements ContainerPreferencesStore {
    private static final ContainerPreferences AUTOMATIC = new ContainerPreferences(null);

    private final Path path;
    private final JsonMapper mapper;
    private volatile Map<ContainerPreferencesKey, ContainerPreferences> snapshot;

    public JsonContainerPreferencesStore(Path path, JsonMapper mapper) {
        this.path = path.toAbsolutePath().normalize();
        // Omit absent values (for example a discovered selection's url) instead of writing explicit nulls.
        this.mapper = mapper.rebuild()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        try {
            snapshot = load();
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
        var updated = new LinkedHashMap<>(snapshot);
        if (selection == null) {
            // Automatic selection is the default, so there is nothing to remember.
            updated.remove(key);
        } else {
            updated.put(key, new ContainerPreferences(selection));
        }
        persist(updated);
        snapshot = Collections.unmodifiableMap(updated);
        return updated.getOrDefault(key, AUTOMATIC);
    }

    private Map<ContainerPreferencesKey, ContainerPreferences> load() throws IOException {
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
        var loaded = new LinkedHashMap<ContainerPreferencesKey, ContainerPreferences>();
        for (var record : stored) {
            if (record == null || record.preferences() == null) {
                throw new IllegalArgumentException("Incomplete container preference record");
            }
            var key = new ContainerPreferencesKey(record.sourceId(), record.containerName());
            ContainerPreferencesValidation.validate(key, record.preferences().selectedEndpoint());
            loaded.put(key, record.preferences());
        }
        return Collections.unmodifiableMap(loaded);
    }

    private void persist(Map<ContainerPreferencesKey, ContainerPreferences> updated) {
        Path temporary = null;
        try {
            var records = updated.entrySet().stream()
                    .map(entry -> new StoredContainerPreferences(
                            entry.getKey().sourceId(), entry.getKey().containerName(), entry.getValue()))
                    .toList();
            var bytes = mapper.writeValueAsBytes(records);
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
