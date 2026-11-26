package com.roomelephant.porthole.dashboard.web;

import com.roomelephant.porthole.dashboard.Dashboard;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding({
    DashboardResponse.class,
    DashboardResponse.SourceState.class,
    DashboardResponse.VersionChecksResponse.class
})
@Schema(description = "Every source's state and every entry, unfiltered")
public record DashboardResponse(
        @Schema(description = "Changes whenever anything shown here changes; GET /api/events signals new ones")
        long revision,

        List<SourceState> sources,
        VersionChecksResponse versionChecks,
        List<EntryResponse> entries) {
    static DashboardResponse from(Dashboard dashboard, IconUrls icons) {
        return new DashboardResponse(
                dashboard.revision(),
                dashboard.sources().stream().map(SourceState::from).toList(),
                new VersionChecksResponse(
                        dashboard.versionChecks().running(),
                        dashboard.versionChecks().lastCompletedAt()),
                dashboard.entries().stream()
                        .map(entry -> EntryResponse.from(entry, icons))
                        .toList());
    }

    @Schema(description = "Manual version checks started with POST /api/version-checks")
    public record VersionChecksResponse(
            @Schema(description = "Whether a check is running")
            boolean running,

            @Schema(description = "When the last check finished, or null if none has since startup", nullable = true)
            @Nullable
            Instant lastCompletedAt) {}

    @Schema(description = "A configured Docker source and what Porthole last knew about it")
    public record SourceState(
            String id,
            String name,
            @Schema(description = "UP, DOWN or ERROR") SourceSnapshot.Status status,

            @Schema(description = "Why the source is not UP", nullable = true) @Nullable
            String error,

            @Schema(description = "When the source last answered, or null if it never has", nullable = true) @Nullable
            Instant lastSuccessAt) {
        static SourceState from(SourceSnapshot snapshot) {
            return new SourceState(
                    snapshot.source().id(),
                    snapshot.source().name(),
                    snapshot.status(),
                    snapshot.error(),
                    snapshot.lastSuccessAt());
        }
    }
}
