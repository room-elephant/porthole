package com.roomelephant.porthole.entries.web;

import com.roomelephant.porthole.entries.EndpointChoice;
import com.roomelephant.porthole.entries.EntryPatch;
import com.roomelephant.porthole.entries.Icon;
import com.roomelephant.porthole.entries.PatchField;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Reads a JSON Merge Patch body, where a missing key keeps a value and {@code null} removes it. */
@NullMarked
final class EntryPatchReader {
    private static final Set<String> FIELDS = Set.of("name", "icon", "endpoint", "stack");
    private static final String NONE = "none";

    private EntryPatchReader() {}

    static EntryPatch read(@Nullable JsonNode body) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException("Body must be a JSON object");
        for (String field : body.propertyNames()) {
            if (!FIELDS.contains(field)) throw new IllegalArgumentException("Unknown field: " + field);
        }
        return new EntryPatch(
                field(body, "name", value -> text(value, "name")),
                field(body, "icon", EntryPatchReader::icon),
                field(body, "endpoint", EntryPatchReader::endpoint),
                field(body, "stack", value -> text(value, "stack")));
    }

    static Icon icon(JsonNode value) {
        Map.Entry<String, JsonNode> only = single(value, "icon must be {\"library\": name} or {\"upload\": id}");
        String text = text(only.getValue(), "icon." + only.getKey());
        return switch (only.getKey()) {
            case "library" -> new Icon.Library(text);
            case "upload" -> new Icon.Upload(text);
            default -> throw new IllegalArgumentException("icon must be {\"library\": name} or {\"upload\": id}");
        };
    }

    private static EndpointChoice endpoint(JsonNode value) {
        String message = "endpoint must be {\"id\": candidate}, {\"url\": url} or \"none\"";
        if (value.isString()) {
            if (NONE.equals(value.stringValue())) return EndpointChoice.NONE;
            throw new IllegalArgumentException(message);
        }
        Map.Entry<String, JsonNode> only = single(value, message);
        String text = text(only.getValue(), "endpoint." + only.getKey());
        return switch (only.getKey()) {
            case "id" -> new EndpointChoice.Discovered(text);
            case "url" -> new EndpointChoice.Custom(text);
            default -> throw new IllegalArgumentException(message);
        };
    }

    private static <T> PatchField<T> field(JsonNode body, String name, Function<JsonNode, T> reader) {
        JsonNode value = body.get(name);
        if (value == null) return PatchField.absent();
        if (value.isNull()) return PatchField.removed();
        return PatchField.set(reader.apply(value));
    }

    private static Map.Entry<String, JsonNode> single(JsonNode value, String message) {
        if (!value.isObject() || value.size() != 1) throw new IllegalArgumentException(message);
        return value.properties().iterator().next();
    }

    private static String text(JsonNode value, String field) {
        if (!value.isString()) throw new IllegalArgumentException(field + " must be a string or null");
        return value.stringValue();
    }
}
