package com.roomelephant.porthole.dashboard.web;

import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.dashboard.ContainerFacts;
import com.roomelephant.porthole.dashboard.DashboardEntry;
import com.roomelephant.porthole.dashboard.DashboardProperties;
import com.roomelephant.porthole.dashboard.IconSource;
import com.roomelephant.porthole.dashboard.ResolvedEndpoint;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.Icon;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

final class WebFixtures {
    static final LinkRef ROUTER = new LinkRef("3f2a9c1e5b7d4e8a9c217d4e5f6a8b90");
    static final String UPLOAD = "9c".repeat(32) + ".webp";
    static final String CDN = "https://cdn.example.com/icons";

    private WebFixtures() {}

    static DashboardEntry sonarr() {
        return new DashboardEntry(
                new ContainerRef("walker", "smh-sonarr-1"),
                "sonarr",
                "smh",
                new IconSource.Library("sonarr"),
                null,
                List.of(new ResolvedEndpoint(
                        "traefik:https://sonarr.example/", "traefik", "https://sonarr.example/", true)),
                true,
                new ContainerFacts(
                        "walker",
                        "smh-sonarr-1",
                        "sonarr",
                        "linuxserver/sonarr:4",
                        "running",
                        "Up 2 hours",
                        false,
                        new VersionInfo("4.0.1", "4.0.2", true)));
    }

    static DashboardEntry router() {
        return new DashboardEntry(
                ROUTER,
                "Router",
                "base",
                new IconSource.Upload(UPLOAD),
                new Icon.Upload(UPLOAD),
                List.of(new ResolvedEndpoint("custom", "custom", "http://192.168.50.1/", true)),
                true,
                null);
    }

    @TestConfiguration
    @Import(IconUrls.class)
    static class Config {
        @Bean
        DashboardProperties dashboardProperties() {
            return new DashboardProperties(new DashboardProperties.Icons("/unused/icons.yml", CDN, ".webp"));
        }
    }
}
