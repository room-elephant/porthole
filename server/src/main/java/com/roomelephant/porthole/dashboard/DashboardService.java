package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshService;
import com.roomelephant.porthole.entries.EntryRef;
import com.roomelephant.porthole.entries.EntryService;
import com.roomelephant.porthole.fleet.Fleet;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

/** Reads the current dashboard from memory: fleet snapshots, saved entries and cached versions. */
@NullMarked
@Service
public class DashboardService {
    private final Fleet fleet;
    private final EntryService entries;
    private final VersionRefreshService versions;
    private final DashboardChanges changes;
    private final DashboardProjector projector;

    public DashboardService(
            Fleet fleet,
            EntryService entries,
            VersionRefreshService versions,
            DashboardChanges changes,
            DashboardProjector projector) {
        this.fleet = fleet;
        this.entries = entries;
        this.versions = versions;
        this.changes = changes;
        this.projector = projector;
    }

    /** Reads the revision first, so the dashboard reflects at least every change that revision counts. */
    public Dashboard current() {
        long revision = changes.revision();
        return projector.project(
                revision, versions.checks(), fleet.snapshots(), entries.state(), versions::cachedVersion);
    }

    /** The resolved entry, or empty for a link that doesn't exist or a container no source lists. */
    public Optional<DashboardEntry> entry(EntryRef ref) {
        return projector.entry(ref, fleet.snapshots(), entries.state(), versions::cachedVersion);
    }
}
