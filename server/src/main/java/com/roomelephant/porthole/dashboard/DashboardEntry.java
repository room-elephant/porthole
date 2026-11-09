package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.entries.EntryRef;
import com.roomelephant.porthole.entries.Icon;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One tile: saved overrides applied over what discovery and defaults resolved.
 *
 * @param iconChoice the icon the user saved, or null when the default is in use
 * @param container Docker facts, or null for a link
 */
@NullMarked
public record DashboardEntry(
        EntryRef ref,
        String name,
        @Nullable String stack,
        IconSource icon,
        @Nullable Icon iconChoice,
        List<ResolvedEndpoint> endpoints,
        boolean hasWebUi,
        @Nullable ContainerFacts container) {
    public DashboardEntry {
        endpoints = List.copyOf(endpoints);
    }
}
