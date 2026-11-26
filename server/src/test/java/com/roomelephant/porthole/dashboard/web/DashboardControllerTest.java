package com.roomelephant.porthole.dashboard.web;

import static com.roomelephant.porthole.dashboard.web.WebFixtures.CDN;
import static com.roomelephant.porthole.dashboard.web.WebFixtures.UPLOAD;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roomelephant.porthole.core.enrichment.version.refresh.VersionChecks;
import com.roomelephant.porthole.dashboard.Dashboard;
import com.roomelephant.porthole.dashboard.DashboardEntry;
import com.roomelephant.porthole.dashboard.DashboardService;
import com.roomelephant.porthole.dashboard.IconSource;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.fleet.Source;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import(WebFixtures.Config.class)
class DashboardControllerTest {
    private static final Instant T = Instant.parse("2026-11-08T10:00:00Z");
    private static final Source WALKER = new Source("walker", "Walker", null, true, Duration.ofSeconds(1));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboard;

    private void serve(DashboardEntry... entries) {
        when(dashboard.current())
                .thenReturn(new Dashboard(
                        42,
                        List.of(SourceSnapshot.initial(WALKER).succeeded(List.of(), T)),
                        List.of(entries),
                        new VersionChecks(false, T)));
    }

    @Test
    void servesSourcesAndEntries() throws Exception {
        serve(WebFixtures.sonarr(), WebFixtures.router());

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].id").value("walker"))
                .andExpect(jsonPath("$.sources[0].name").value("Walker"))
                .andExpect(jsonPath("$.sources[0].status").value("UP"))
                .andExpect(jsonPath("$.sources[0].error").doesNotExist())
                .andExpect(jsonPath("$.sources[0].lastSuccessAt").value("2026-11-08T10:00:00Z"))
                .andExpect(jsonPath("$.entries[0].id").value("container:walker:smh-sonarr-1"))
                .andExpect(jsonPath("$.entries[0].kind").value("container"))
                .andExpect(jsonPath("$.entries[0].name").value("sonarr"))
                .andExpect(jsonPath("$.entries[0].stack").value("smh"))
                .andExpect(jsonPath("$.entries[0].icon.url").value(CDN + "/sonarr.webp"))
                .andExpect(jsonPath("$.entries[0].icon.choice").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.entries[0].endpoints[0].id").value("traefik:https://sonarr.example/"))
                .andExpect(jsonPath("$.entries[0].endpoints[0].source").value("traefik"))
                .andExpect(jsonPath("$.entries[0].endpoints[0].url").value("https://sonarr.example/"))
                .andExpect(jsonPath("$.entries[0].endpoints[0].selected").value(true))
                .andExpect(jsonPath("$.entries[0].hasWebUi").value(true))
                .andExpect(jsonPath("$.entries[0].container.sourceId").value("walker"))
                .andExpect(jsonPath("$.entries[0].container.name").value("smh-sonarr-1"))
                .andExpect(jsonPath("$.entries[0].container.defaultName").value("sonarr"))
                .andExpect(jsonPath("$.entries[0].container.image").value("linuxserver/sonarr:4"))
                .andExpect(jsonPath("$.entries[0].container.state").value("running"))
                .andExpect(jsonPath("$.entries[0].container.status").value("Up 2 hours"))
                .andExpect(jsonPath("$.entries[0].container.self").value(false))
                .andExpect(jsonPath("$.entries[0].container.version.currentVersion")
                        .value("4.0.1"))
                .andExpect(
                        jsonPath("$.entries[0].container.version.latestVersion").value("4.0.2"))
                .andExpect(jsonPath("$.entries[0].container.version.updateAvailable")
                        .value(true))
                .andExpect(jsonPath("$.entries[1].id").value(WebFixtures.ROUTER.id()))
                .andExpect(jsonPath("$.entries[1].kind").value("link"))
                .andExpect(jsonPath("$.entries[1].icon.url").value("/api/icons/" + UPLOAD))
                .andExpect(jsonPath("$.entries[1].icon.choice.upload").value(UPLOAD))
                .andExpect(jsonPath("$.entries[1].icon.choice.library").doesNotExist())
                .andExpect(jsonPath("$.entries[1].container").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void libraryChoiceAndSelfAndNoneIcons() throws Exception {
        DashboardEntry sonarr = WebFixtures.sonarr();
        DashboardEntry chosen = new DashboardEntry(
                sonarr.ref(),
                sonarr.name(),
                sonarr.stack(),
                new IconSource.Library("tv"),
                new com.roomelephant.porthole.entries.Icon.Library("tv"),
                sonarr.endpoints(),
                true,
                sonarr.container());
        DashboardEntry self = new DashboardEntry(
                new ContainerRef("walker", "porthole"),
                "porthole",
                null,
                new IconSource.Self(),
                null,
                List.of(),
                false,
                sonarr.container());
        DashboardEntry router = WebFixtures.router();
        DashboardEntry bare = new DashboardEntry(
                router.ref(), router.name(), null, new IconSource.None(), null, router.endpoints(), true, null);
        serve(chosen, self, bare);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.entries[0].icon.url").value(CDN + "/tv.webp"))
                .andExpect(jsonPath("$.entries[0].icon.choice.library").value("tv"))
                .andExpect(jsonPath("$.entries[1].icon.url").value("porthole-mark.svg"))
                .andExpect(jsonPath("$.entries[2].icon.url").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.entries[2].icon.choice").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.entries[2].stack").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void emptyDashboardIs200() throws Exception {
        when(dashboard.current()).thenReturn(new Dashboard(1, List.of(), List.of(), new VersionChecks(true, null)));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.versionChecks.running").value(true))
                .andExpect(jsonPath("$.versionChecks.lastCompletedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void servesRevisionAndVersionChecks() throws Exception {
        serve();

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(42))
                .andExpect(jsonPath("$.versionChecks.running").value(false))
                .andExpect(jsonPath("$.versionChecks.lastCompletedAt").value("2026-11-08T10:00:00Z"));
    }
}
