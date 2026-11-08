package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.fleet.ContainerSnapshot;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A short, human-readable account of how a source's snapshot changed, for the revision log. */
@NullMarked
final class SnapshotDiff {
    private SnapshotDiff() {}

    static String describe(@Nullable SourceSnapshot before, SourceSnapshot after) {
        String source = after.source().id();
        if (before == null) {
            return after.status() == SourceSnapshot.Status.UP
                    ? source + " UP: first listing, " + after.containers().size() + " containers"
                    : source + " " + after.status() + " (" + after.error() + ")";
        }
        if (before.status() != after.status()) {
            String reason = after.error() == null ? "" : " (" + after.error() + ")";
            return source + ": " + before.status() + " → " + after.status() + reason;
        }
        List<String> parts = new ArrayList<>();
        Map<String, ContainerSnapshot> old = byName(before.containers());
        Map<String, ContainerSnapshot> now = byName(after.containers());
        List<String> added =
                now.keySet().stream().filter(name -> !old.containsKey(name)).toList();
        List<String> removed =
                old.keySet().stream().filter(name -> !now.containsKey(name)).toList();
        for (Map.Entry<String, ContainerSnapshot> entry : now.entrySet()) {
            ContainerSnapshot previous = old.get(entry.getKey());
            if (previous != null) {
                String changes = changes(previous, entry.getValue());
                if (!changes.isEmpty()) parts.add(entry.getKey() + " " + changes);
            }
        }
        List<String> sections = new ArrayList<>();
        if (!parts.isEmpty()) sections.add(String.join("; ", parts));
        if (!added.isEmpty()) sections.add("added " + String.join(", ", added));
        if (!removed.isEmpty()) sections.add("removed " + String.join(", ", removed));
        if (!Objects.equals(before.error(), after.error())) sections.add("error " + after.error());
        return source + ": " + (sections.isEmpty() ? "changed" : String.join("; ", sections));
    }

    private static Map<String, ContainerSnapshot> byName(List<ContainerSnapshot> containers) {
        Map<String, ContainerSnapshot> named = new LinkedHashMap<>();
        for (ContainerSnapshot container : containers) named.put(container.name(), container);
        return named;
    }

    private static String changes(ContainerSnapshot before, ContainerSnapshot after) {
        List<String> changes = new ArrayList<>();
        field(changes, "state", before, after, ContainerSnapshot::state, false);
        field(changes, "status", before, after, ContainerSnapshot::status, true);
        field(changes, "image", before, after, ContainerSnapshot::image, false);
        field(changes, "image ID", before, after, ContainerSnapshot::imageId, false);
        field(changes, "ports", before, after, c -> new TreeSet<>(c.publishedPorts()).toString(), false);
        if (!before.labels().equals(after.labels())) changes.add("labels changed");
        return String.join(", ", changes);
    }

    private static void field(
            List<String> changes,
            String label,
            ContainerSnapshot before,
            ContainerSnapshot after,
            Function<ContainerSnapshot, @Nullable String> value,
            boolean quoted) {
        String old = value.apply(before);
        String now = value.apply(after);
        if (Objects.equals(old, now)) return;
        changes.add(label + " " + text(old, quoted) + " → " + text(now, quoted));
    }

    private static String text(@Nullable String value, boolean quoted) {
        if (value == null) return "none";
        return quoted ? "\"" + value + "\"" : value;
    }
}
