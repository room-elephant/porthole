package com.roomelephant.porthole.entries;

import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What the user saved for one entry. For a container every field is an optional override of what Docker discovery
 * supplies; a link has its own name and URL. Values are trimmed and checked on construction.
 */
@NullMarked
public record EntryRecord(
        EntryRef ref,
        @Nullable String name,
        @Nullable Icon icon,
        @Nullable EndpointChoice endpoint,
        @Nullable String stack) {
    static final int MAX_TEXT_LENGTH = 255;

    public EntryRecord {
        name = name == null ? null : requireName(name);
        stack = normalizeStack(stack);
        switch (ref) {
            case LinkRef _ -> {
                if (name == null) throw new IllegalArgumentException("A link needs a name");
                if (endpoint == null) throw new IllegalArgumentException("A link needs a URL");
                if (!(endpoint instanceof EndpointChoice.Custom))
                    throw new IllegalArgumentException("A link's endpoint must be a URL");
            }
            case ContainerRef _ -> {
                if (stack != null)
                    throw new IllegalArgumentException("Containers take their stack from Docker Compose");
            }
        }
    }

    /** A container record with no overrides. */
    public static EntryRecord container(ContainerRef ref) {
        return new EntryRecord(ref, null, null, null, null);
    }

    /** A container record that overrides nothing; it is not stored. */
    public boolean isEmpty() {
        return ref instanceof ContainerRef && name == null && icon == null && endpoint == null;
    }

    private static String requireName(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("name must not be blank");
        requireLength(trimmed, "name");
        return trimmed;
    }

    private static @Nullable String normalizeStack(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        requireLength(trimmed, "stack");
        return trimmed;
    }

    private static void requireLength(String value, String field) {
        if (value.length() > MAX_TEXT_LENGTH)
            throw new IllegalArgumentException(field + " must be at most " + MAX_TEXT_LENGTH + " characters");
    }
}
