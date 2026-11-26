package com.roomelephant.porthole.entries;

import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/** Every saved entry, in the order they were first saved. Immutable; changes return a new state. */
@NullMarked
public record EntryState(List<EntryRecord> entries) {
    public static final EntryState EMPTY = new EntryState(List.of());

    public EntryState {
        entries = List.copyOf(entries);
        Set<EntryRef> seen = new HashSet<>();
        for (EntryRecord entry : entries) {
            if (!seen.add(entry.ref()))
                throw new IllegalArgumentException(
                        "Duplicate entry id: " + entry.ref().id());
        }
    }

    public Optional<EntryRecord> find(EntryRef ref) {
        return entries.stream().filter(entry -> entry.ref().equals(ref)).findFirst();
    }

    /** Replaces the record with the same ref in place, or appends it. A container record with no overrides is dropped. */
    public EntryState with(EntryRecord record) {
        List<EntryRecord> updated = new ArrayList<>(entries.size() + 1);
        boolean replaced = false;
        for (EntryRecord entry : entries) {
            if (entry.ref().equals(record.ref())) {
                replaced = true;
                if (!record.isEmpty()) updated.add(record);
            } else {
                updated.add(entry);
            }
        }
        if (!replaced && !record.isEmpty()) updated.add(record);
        return new EntryState(updated);
    }

    public EntryState without(EntryRef ref) {
        return new EntryState(
                entries.stream().filter(entry -> !entry.ref().equals(ref)).toList());
    }

    public long linkCount() {
        return entries.stream().filter(entry -> entry.ref() instanceof LinkRef).count();
    }

    /** The uploaded icon files that some entry uses. */
    public Set<String> uploadIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (EntryRecord entry : entries) {
            if (entry.icon() instanceof Icon.Upload upload) ids.add(upload.id());
        }
        return Set.copyOf(ids);
    }
}
