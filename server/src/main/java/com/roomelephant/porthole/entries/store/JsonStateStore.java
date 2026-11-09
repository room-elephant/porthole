package com.roomelephant.porthole.entries.store;

import com.roomelephant.porthole.entries.EndpointChoice;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef;
import com.roomelephant.porthole.entries.EntryState;
import com.roomelephant.porthole.entries.Icon;
import com.roomelephant.porthole.entries.StateStore;
import com.roomelephant.porthole.shared.AtomicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Keeps entries in one JSON file with a schema version. Maps each field by hand, so the file format never follows a
 * refactor by accident and the native image needs no reflection hints for it.
 */
@NullMarked
public final class JsonStateStore implements StateStore {
    public static final int SCHEMA_VERSION = 1;

    private static final String NONE = "none";

    private final Path path;
    private final JsonMapper mapper;

    public JsonStateStore(Path path, JsonMapper mapper) {
        this.path = path.toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    @Override
    public EntryState load() {
        byte[] contents;
        try {
            contents = Files.readAllBytes(path);
        } catch (NoSuchFileException _) {
            return EntryState.EMPTY;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read entries file " + path, e);
        }
        try {
            return read(mapper.readTree(contents));
        } catch (JacksonException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid entries file " + path + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void save(EntryState state) {
        try {
            AtomicFiles.replace(path, mapper.writeValueAsBytes(write(state)), ".state-");
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Failed to save entries", e);
        }
    }

    private EntryState read(@Nullable JsonNode root) {
        if (root == null || !root.isObject()) throw new IllegalArgumentException("expected a JSON object");
        JsonNode version = root.get("schemaVersion");
        if (version == null || !version.isInt()) throw new IllegalArgumentException("missing schemaVersion");
        int schemaVersion = version.intValue();
        if (schemaVersion < 1 || schemaVersion > SCHEMA_VERSION)
            throw new IllegalArgumentException(
                    "unsupported schemaVersion " + schemaVersion + "; this Porthole reads up to " + SCHEMA_VERSION);
        JsonNode entries = root.get("entries");
        if (entries == null || !entries.isArray()) throw new IllegalArgumentException("missing entries array");
        List<EntryRecord> records = new ArrayList<>();
        for (JsonNode entry : entries) records.add(readEntry(entry));
        return new EntryState(records);
    }

    private EntryRecord readEntry(JsonNode entry) {
        if (!entry.isObject()) throw new IllegalArgumentException("each entry must be an object");
        String id = requireString(entry, "id");
        EntryRef ref;
        try {
            ref = EntryRef.parse(id);
        } catch (RuntimeException _) {
            throw new IllegalArgumentException("invalid entry id " + id);
        }
        return new EntryRecord(
                ref,
                optionalString(entry, "name"),
                readIcon(entry.get("icon")),
                readEndpoint(entry.get("endpoint")),
                optionalString(entry, "stack"));
    }

    private static @Nullable Icon readIcon(@Nullable JsonNode icon) {
        if (icon == null) return null;
        Map.Entry<String, JsonNode> only = single(icon, "icon");
        String value = requireString(only.getValue(), "icon." + only.getKey());
        return switch (only.getKey()) {
            case "library" -> new Icon.Library(value);
            case "upload" -> new Icon.Upload(value);
            default -> throw new IllegalArgumentException("unknown icon kind " + only.getKey());
        };
    }

    private static @Nullable EndpointChoice readEndpoint(@Nullable JsonNode endpoint) {
        if (endpoint == null) return null;
        if (endpoint.isString() && NONE.equals(endpoint.stringValue())) return EndpointChoice.NONE;
        Map.Entry<String, JsonNode> only = single(endpoint, "endpoint");
        String value = requireString(only.getValue(), "endpoint." + only.getKey());
        return switch (only.getKey()) {
            case "id" -> new EndpointChoice.Discovered(value);
            case "url" -> new EndpointChoice.Custom(value);
            default -> throw new IllegalArgumentException("unknown endpoint kind " + only.getKey());
        };
    }

    private static Map.Entry<String, JsonNode> single(JsonNode node, String field) {
        if (!node.isObject() || node.size() != 1)
            throw new IllegalArgumentException(field + " must be an object with one field");
        return node.properties().iterator().next();
    }

    private static String requireString(JsonNode node, String field) {
        JsonNode value = node.isObject() ? node.get(field) : node;
        if (value == null || !value.isString()) throw new IllegalArgumentException(field + " must be a string");
        return value.stringValue();
    }

    private static @Nullable String optionalString(JsonNode entry, String field) {
        JsonNode value = entry.get(field);
        if (value == null) return null;
        if (!value.isString()) throw new IllegalArgumentException(field + " must be a string");
        return value.stringValue();
    }

    private ObjectNode write(EntryState state) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        ArrayNode entries = root.putArray("entries");
        for (EntryRecord record : state.entries()) {
            ObjectNode entry = entries.addObject();
            entry.put("id", record.ref().id());
            if (record.name() != null) entry.put("name", record.name());
            writeIcon(entry, record.icon());
            writeEndpoint(entry, record.endpoint());
            if (record.stack() != null) entry.put("stack", record.stack());
        }
        return root;
    }

    private static void writeIcon(ObjectNode entry, @Nullable Icon icon) {
        switch (icon) {
            case null -> {}
            case Icon.Library library -> entry.putObject("icon").put("library", library.name());
            case Icon.Upload upload -> entry.putObject("icon").put("upload", upload.id());
        }
    }

    private static void writeEndpoint(ObjectNode entry, @Nullable EndpointChoice endpoint) {
        switch (endpoint) {
            case null -> {}
            case EndpointChoice.Discovered discovered ->
                entry.putObject("endpoint").put("id", discovered.id());
            case EndpointChoice.Custom custom -> entry.putObject("endpoint").put("url", custom.url());
            case EndpointChoice.None _ -> entry.put("endpoint", NONE);
        }
    }
}
