package com.roomelephant.porthole.entries;

import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;

/** Identifies an entry: a Docker container by source and name, or a link by its generated ID. */
@NullMarked
public sealed interface EntryRef permits EntryRef.ContainerRef, EntryRef.LinkRef {
    int MAX_SEGMENT_LENGTH = 255;

    /** The opaque ID clients use, such as {@code container:walker:app} or {@code link:<32 hex>}. */
    String id();

    /** @throws EntryNotFoundException when the ID is malformed, since it then names no entry */
    static EntryRef parse(String id) {
        String[] parts = id.split(":", -1);
        try {
            if (parts.length == 3 && ContainerRef.KIND.equals(parts[0])) return new ContainerRef(parts[1], parts[2]);
            if (parts.length == 2 && LinkRef.KIND.equals(parts[0])) return new LinkRef(parts[1]);
        } catch (IllegalArgumentException _) {
            // Falls through: a malformed ID identifies nothing.
        }
        throw new EntryNotFoundException(id);
    }

    record ContainerRef(String sourceId, String name) implements EntryRef {
        static final String KIND = "container";
        private static final Pattern SOURCE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]*");
        private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_.-]*");

        public ContainerRef {
            require(SOURCE_ID, sourceId, "sourceId");
            require(NAME, name, "container name");
        }

        @Override
        public String id() {
            return KIND + ":" + sourceId + ":" + name;
        }

        private static void require(Pattern pattern, String value, String field) {
            if (value.length() > MAX_SEGMENT_LENGTH || !pattern.matcher(value).matches())
                throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
    }

    record LinkRef(String hex) implements EntryRef {
        static final String KIND = "link";
        private static final Pattern HEX = Pattern.compile("[0-9a-f]{32}");

        public LinkRef {
            if (!HEX.matcher(hex).matches()) throw new IllegalArgumentException("Invalid link id: " + hex);
        }

        public static LinkRef random() {
            UUID uuid = UUID.randomUUID();
            HexFormat hexFormat = HexFormat.of();
            return new LinkRef(hexFormat.toHexDigits(uuid.getMostSignificantBits())
                    + hexFormat.toHexDigits(uuid.getLeastSignificantBits()));
        }

        @Override
        public String id() {
            return KIND + ":" + hex;
        }
    }
}
