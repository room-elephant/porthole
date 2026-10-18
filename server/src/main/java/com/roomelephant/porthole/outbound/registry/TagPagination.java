package com.roomelephant.porthole.outbound.registry;

import com.roomelephant.porthole.core.model.TagCatalogue;
import com.roomelephant.porthole.outbound.registry.distribution.RegistryProtocolException;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.HttpClientErrorException;

@NullMarked
public final class TagPagination {
    private static final int MAX_PAGES = 100;

    private TagPagination() {}

    @FunctionalInterface
    public interface PageFetcher {
        TagPage fetch(URI current);
    }

    public static TagCatalogue read(URI first, Duration timeout, PageFetcher fetcher) {
        return read(first, timeout, fetcher, System::nanoTime);
    }

    static TagCatalogue read(URI first, Duration timeout, PageFetcher fetcher, LongSupplier nanos) {
        URI next = first;
        Set<URI> visited = new HashSet<>();
        List<String> tags = new ArrayList<>();
        long started = nanos.getAsLong();
        long budget = timeout.toNanos();
        for (int page = 0; page < MAX_PAGES; page++) {
            if (!visited.add(next) || !sameCatalogue(first, next))
                throw new RegistryProtocolException("Incomplete or unsafe registry catalogue");
            checkBudget(nanos, started, budget);
            TagPage result;
            try {
                result = fetcher.fetch(next);
            } catch (HttpClientErrorException.NotFound e) {
                if (page == 0) throw e;
                throw new RegistryProtocolException("Incomplete registry catalogue", e);
            }
            tags.addAll(result.tags());
            checkBudget(nanos, started, budget);
            @Nullable URI following = result.next();
            if (following == null) return new TagCatalogue(tags);
            next = following;
        }
        throw new RegistryProtocolException("Registry catalogue exceeded the page limit");
    }

    private static void checkBudget(LongSupplier nanos, long started, long budget) {
        if (nanos.getAsLong() - started > budget)
            throw new RegistryProtocolException("Registry catalogue exceeded the read budget");
    }

    public static URI resolve(URI current, String target) {
        try {
            return target.startsWith("?")
                    ? URI.create(current.toString().split("\\?", 2)[0] + target)
                    : current.resolve(target);
        } catch (IllegalArgumentException e) {
            throw new RegistryProtocolException("Invalid pagination URI", e);
        }
    }

    private static boolean sameCatalogue(URI first, URI next) {
        return first.getScheme().equalsIgnoreCase(next.getScheme())
                && first.getRawAuthority().equalsIgnoreCase(next.getRawAuthority())
                && first.getRawPath().equals(next.getRawPath())
                && next.getFragment() == null
                && next.getUserInfo() == null;
    }
}
