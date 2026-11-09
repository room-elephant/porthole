package com.roomelephant.porthole.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.dashboard.DashboardProperties;
import com.roomelephant.porthole.dashboard.IconSource;
import org.junit.jupiter.api.Test;

class IconUrlsTest {
    private final IconUrls urls = new IconUrls(
            new DashboardProperties(new DashboardProperties.Icons("/unused", "https://cdn.example.com/icons", ".svg")));

    @Test
    void libraryIconUsesConfiguredCdn() {
        assertThat(urls.url(new IconSource.Library("coredns"))).isEqualTo("https://cdn.example.com/icons/coredns.svg");
    }

    @Test
    void uploadIsRelative() {
        assertThat(urls.url(new IconSource.Upload("a.png"))).isEqualTo("/api/icons/a.png");
    }

    @Test
    void selfIsPortholeMark() {
        assertThat(urls.url(new IconSource.Self())).isEqualTo("porthole-mark.svg");
    }

    @Test
    void noneHasNoUrl() {
        assertThat(urls.url(new IconSource.None())).isNull();
    }
}
