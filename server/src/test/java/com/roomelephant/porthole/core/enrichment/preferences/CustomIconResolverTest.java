package com.roomelephant.porthole.core.enrichment.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.enrichment.icon.IconSettings;
import com.roomelephant.porthole.core.model.SelectedIcon;
import org.junit.jupiter.api.Test;

class CustomIconResolverTest {
    private final CustomIconResolver resolver =
            new CustomIconResolver(new IconSettings("https://cdn.example.com/icons", ".svg"));

    @Test
    void libraryIconUsesTheConfiguredCdn() {
        assertThat(resolver.resolve(SelectedIcon.library("coredns")))
                .isEqualTo("https://cdn.example.com/icons/coredns.svg");
    }

    @Test
    void uploadedIconUsesARelativePath() {
        String name = "0123456789abcdef0123456789abcdef.webp";
        assertThat(resolver.resolve(SelectedIcon.upload(name))).isEqualTo("/api/icons/" + name);
    }
}
