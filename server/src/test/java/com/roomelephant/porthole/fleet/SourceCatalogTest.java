package com.roomelephant.porthole.fleet;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceCatalogTest {
    private static Source source(String id) {
        return new Source(id, id.toUpperCase(), null, false, Duration.ofSeconds(1));
    }

    @Test
    void requireReturnsConfiguredSource() {
        SourceCatalog catalog = new SourceCatalog(List.of(source("walker"), source("mike")));
        assertThat(catalog.require("mike")).isEqualTo(source("mike"));
    }

    @Test
    void requireRejectsUnknownWithMessage() {
        SourceCatalog catalog = new SourceCatalog(List.of(source("walker")));
        assertThatThrownBy(() -> catalog.require("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown sourceId: nope");
    }

    @Test
    void rejectsEmptyAndDuplicateIds() {
        assertThatThrownBy(() -> new SourceCatalog(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceCatalog(List.of(source("a"), source("a"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containsMatchesOnlyConfiguredIds() {
        SourceCatalog catalog = new SourceCatalog(List.of(source("walker")));
        assertThat(catalog.contains("walker")).isTrue();
        assertThat(catalog.contains("mike")).isFalse();
    }

    @Test
    void sourcesKeepConfigurationOrder() {
        SourceCatalog catalog = new SourceCatalog(List.of(source("b"), source("a"), source("c")));
        assertThat(catalog.sources()).extracting(Source::id).containsExactly("b", "a", "c");
    }
}
