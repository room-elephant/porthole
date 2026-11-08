package com.roomelephant.porthole.core.enrichment.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshService;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.fleet.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class VersionEnricherTest {
    private final EnrichmentContext context = new EnrichmentContext("local", "localhost", false, false);

    private Container container() {
        return new Container(
                "a",
                "local",
                "app",
                "app",
                "nginx:1.0",
                new Container.Details(Set.of(), Map.of(), null, "running", "Up"),
                List.of());
    }

    @Test
    void attachesCachedValueToTheSameContainer() {
        var cache = mock(VersionRefreshService.class);
        var version = new VersionInfo("1.0", "2.0", true);
        when(cache.cachedVersion(new com.roomelephant.porthole.fleet.ContainerKey("local", "a")))
                .thenReturn(version);
        var container = container();
        assertThat(new VersionEnricher(cache).enrich(List.of(container), context))
                .containsExactly(container);
        assertThat(container.version()).isEqualTo(version);
    }

    @Test
    void readsCachedVersionOnly() {
        var cache = mock(VersionRefreshService.class);
        var container = container();
        new VersionEnricher(cache).enrich(List.of(container), context);
        assertThat(container.version()).isNull();
        verify(cache).cachedVersion(new ContainerKey("local", "a"));
        verifyNoMoreInteractions(cache);
    }
}
