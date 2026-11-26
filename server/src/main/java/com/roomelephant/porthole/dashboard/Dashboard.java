package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.fleet.SourceSnapshot;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/** Everything the dashboard shows: each source's state and every entry, unfiltered. */
@NullMarked
public record Dashboard(List<SourceSnapshot> sources, List<DashboardEntry> entries) {
    public Dashboard {
        sources = List.copyOf(sources);
        entries = List.copyOf(entries);
    }
}
