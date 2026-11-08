package com.roomelephant.porthole.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.roomelephant.porthole.core.enrichment.version.refresh.VersionChecks;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshService;
import com.roomelephant.porthole.core.model.SelfContainer;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.endpoints.EndpointDiscovery;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryService;
import com.roomelephant.porthole.entries.EntryState;
import com.roomelephant.porthole.fleet.ContainerKey;
import com.roomelephant.porthole.fleet.ContainerSnapshot;
import com.roomelephant.porthole.fleet.Fleet;
import com.roomelephant.porthole.fleet.Source;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
    private static final Source WALKER = new Source("walker", "Walker", null, true, Duration.ofSeconds(1));

    @Test
    void combinesFleetEntriesAndVersions() {
        Fleet fleet = mock(Fleet.class);
        EntryService entries = mock(EntryService.class);
        VersionRefreshService versions = mock(VersionRefreshService.class);
        ContainerSnapshot app =
                new ContainerSnapshot("app-id", "app", "nginx", "img", Set.of(), Map.of(), "running", "Up");
        when(fleet.snapshots())
                .thenReturn(List.of(SourceSnapshot.initial(WALKER).succeeded(List.of(app), Instant.EPOCH)));
        ContainerRef ref = new ContainerRef("walker", "app");
        when(entries.state()).thenReturn(EntryState.EMPTY.with(new EntryRecord(ref, "App", null, null, null)));
        VersionInfo version = new VersionInfo("1", "2", true);
        when(versions.cachedVersion(new ContainerKey("walker", "app-id"))).thenReturn(version);
        DashboardChanges changes = mock(DashboardChanges.class);
        when(changes.revision()).thenReturn(5L);
        VersionChecks checks = new VersionChecks(true, null);
        when(versions.checks()).thenReturn(checks);
        DashboardService service = new DashboardService(
                fleet,
                entries,
                versions,
                changes,
                new DashboardProjector(
                        new EndpointDiscovery(List.of()), new DefaultIcons(Map.of()), SelfContainer.none()));

        Dashboard dashboard = service.current();

        assertThat(dashboard.revision()).isEqualTo(5L);
        assertThat(dashboard.versionChecks()).isEqualTo(checks);

        assertThat(dashboard.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.name()).isEqualTo("App");
            assertThat(entry.container().version()).isEqualTo(version);
        });
        assertThat(service.entry(ref)).map(DashboardEntry::name).contains("App");
        assertThat(service.entry(new ContainerRef("walker", "gone"))).isEmpty();
    }
}
