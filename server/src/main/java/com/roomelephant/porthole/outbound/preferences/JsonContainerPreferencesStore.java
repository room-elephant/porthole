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
import java.util.List;
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
        Map<ContainerPreferencesKey, ContainerPreferences> updated = new LinkedHashMap<>(snapshot);
        if (selection == null) {
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
            return Map.of();
        }
        StoredContainerPreferences[] stored = mapper.readValue(contents, StoredContainerPreferences[].class);
        if (stored == null) {
            throw new IllegalArgumentException("Expected an array of container preference records");
        }
        Map<ContainerPreferencesKey, ContainerPreferences> loaded = new LinkedHashMap<>();
        for (StoredContainerPreferences record : stored) {
            if (record == null || record.preferences() == null) {
                throw new IllegalArgumentException("Incomplete container preference record");
            }
            ContainerPreferencesKey key = new ContainerPreferencesKey(record.sourceId(), record.containerName());
            ContainerPreferencesValidation.validate(key, record.preferences().selectedEndpoint());
            loaded.put(key, record.preferences());
        }
        return Collections.unmodifiableMap(loaded);
    }

    private void persist(Map<ContainerPreferencesKey, ContainerPreferences> updated) {
        Path temporary = null;
        try {
            List<StoredContainerPreferences> records = updated.entrySet().stream()
                    .map(entry -> new StoredContainerPreferences(
                            entry.getKey().sourceId(), entry.getKey().containerName(), entry.getValue()))
                    .toList();
            byte[] bytes = mapper.writeValueAsBytes(records);
            Path parent = Objects.requireNonNull(path.getParent());
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".preferences-", ".tmp");
            writeDurably(temporary, bytes);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (FileSystemException moveFailure) {
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

    private static void writeDurably(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(
                target, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }
}
