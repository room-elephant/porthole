package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.fleet.SourceSnapshot;
import com.roomelephant.porthole.versions.VersionChecks;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/** Everything the dashboard shows: its revision, each source's state, every entry unfiltered, and version checks. */
@NullMarked
public record Dashboard(
        long revision, List<SourceSnapshot> sources, List<DashboardEntry> entries, VersionChecks versionChecks) {
    public Dashboard {
        sources = List.copyOf(sources);
        entries = List.copyOf(entries);
    }
}
