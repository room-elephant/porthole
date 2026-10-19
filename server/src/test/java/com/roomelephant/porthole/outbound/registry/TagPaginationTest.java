package com.roomelephant.porthole.outbound.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.roomelephant.porthole.outbound.registry.distribution.RegistryProtocolException;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TagPaginationTest {
    private static final URI FIRST = URI.create("https://registry/repo/tags?n=100");

    @ParameterizedTest
    @ValueSource(strings = {"?page=2", "/repo/tags?page=2", "https://registry/repo/tags?page=2"})
    void returnsAllRawTags(String target) {
        var result = TagPagination.read(
                FIRST,
                Duration.ofSeconds(10),
                page -> page.equals(FIRST)
                        ? new TagPage(List.of("latest", "v1.0"), TagPagination.resolve(page, target))
                        : new TagPage(List.of("1.0.0", "7", "1.0-alpine"), null));
        assertEquals(List.of("latest", "v1.0", "1.0.0", "7", "1.0-alpine"), result.tags());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://evil/repo/tags",
                "/other/tags",
                "?n=100",
                "?page=2#fragment",
                "https://user@registry/repo/tags"
            })
    void rejectsUnsafeAndCyclicLinks(String target) {
        var count = new AtomicInteger();
        var timeout = Duration.ofSeconds(10);
        TagPagination.PageFetcher fetcher = page -> {
            count.incrementAndGet();
            return new TagPage(List.of("9.0"), TagPagination.resolve(page, target));
        };
        assertThrows(RegistryProtocolException.class, () -> TagPagination.read(FIRST, timeout, fetcher));
        assertEquals(1, count.get());
    }

    @Test
    void incompleteCatalogueNeverReturnsPartialTags() {
        var count = new AtomicInteger();
        var timeout = Duration.ofSeconds(10);
        TagPagination.PageFetcher fetcher = page ->
                new TagPage(List.of("9.0"), URI.create("https://registry/repo/tags?page=" + count.incrementAndGet()));
        assertThrows(RegistryProtocolException.class, () -> TagPagination.read(FIRST, timeout, fetcher));
        assertEquals(100, count.get());
    }

    @Test
    void emptyCatalogueIsSuccessful() {
        assertTrue(TagPagination.read(FIRST, Duration.ofSeconds(10), page -> new TagPage(List.of(), null))
                .tags()
                .isEmpty());
    }

    @Test
    void checksBudgetBeforeStartingAndAfterCompletingAPage() {
        var nanos = new AtomicLong();
        var requests = new AtomicInteger();
        var timeout = Duration.ofNanos(10);
        TagPagination.PageFetcher fetcher = page -> {
            requests.incrementAndGet();
            nanos.set(11);
            return new TagPage(List.of("9.0"), null);
        };
        assertThrows(RegistryProtocolException.class, () -> TagPagination.read(FIRST, timeout, fetcher, nanos::get));
        assertEquals(1, requests.get());
    }
}
