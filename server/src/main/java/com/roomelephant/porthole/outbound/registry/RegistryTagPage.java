package com.roomelephant.porthole.outbound.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.roomelephant.porthole.core.model.ImageUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.HttpClientErrorException;

/** Provider-specific catalogue formats share numeric selection and safe navigation. */
@NullMarked
public record RegistryTagPage(
        @Nullable String best, @Nullable URI next) {
    public static RegistryTagPage hub(JsonNode response, URI current) {
        JsonNode results = response.path("results");
        if (!results.isArray()) throw new IllegalArgumentException("Missing tag results");
        JsonNode link = response.get("next");
        if (link == null || link.isNull()) return new RegistryTagPage(bestTag(results, true), null);
        if (!link.isTextual() || link.asText().isBlank()) throw new IllegalArgumentException("Invalid next page");
        return new RegistryTagPage(bestTag(results, true), resolve(current, link.asText()));
    }

    public static RegistryTagPage distribution(JsonNode response, List<String> links, URI current) {
        JsonNode tags = response.path("tags");
        if (!tags.isArray()) throw new IllegalArgumentException("Missing tag list");
        String next = RegistryLink.next(links);
        return new RegistryTagPage(bestTag(tags, false), next == null ? null : resolve(current, next));
    }

    @FunctionalInterface
    public interface PageFetcher {
        RegistryTagPage fetch(URI current) throws IOException;
    }

    public static @Nullable String latest(URI first, Duration readTimeout, PageFetcher pages) {
        URI next = first;
        HashSet<URI> visited = new HashSet<>();
        String best = null;
        long started = System.nanoTime();
        long budget = readTimeout.toNanos();
        try {
            for (int page = 0; page < 100; page++) {
                if (!visited.add(next) || !RegistryTagPage.sameCatalogue(first, next)) {
                    throw new IllegalStateException("Incomplete or unsafe registry catalogue");
                }
                RegistryTagPage result = pages.fetch(next);
                if (System.nanoTime() - started > budget) {
                    throw new IllegalStateException("Registry catalogue exceeded the read budget");
                }
                best = RegistryTagPage.higherTag(best, result.best());
                URI following = result.next();
                if (following == null) return best;
                next = following;
            }
        } catch (HttpClientErrorException.NotFound e) {
            if (next.equals(first)) return null;
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid registry tag response", e);
        }
        throw new IllegalStateException("Registry catalogue exceeded the page limit");
    }

    static URI resolve(URI current, String target) {
        // URI.resolve loses the last path component for a query-only reference.
        return target.startsWith("?")
                ? URI.create(current.toString().split("\\?", 2)[0] + target)
                : current.resolve(target);
    }

    static boolean sameCatalogue(URI first, URI next) {
        return first.getScheme().equalsIgnoreCase(next.getScheme())
                && first.getRawAuthority().equalsIgnoreCase(next.getRawAuthority())
                && first.getRawPath().equals(next.getRawPath())
                && next.getFragment() == null
                && next.getUserInfo() == null;
    }

    static @Nullable String higherTag(@Nullable String left, @Nullable String right) {
        if (left == null) return right;
        if (right == null) return left;
        int comparison = ImageUtils.compareSemVer(right, left);
        return comparison > 0 || (comparison == 0 && right.compareTo(left) > 0) ? right : left;
    }

    private static @Nullable String bestTag(JsonNode results, boolean hub) {
        String best = null;
        for (JsonNode result : results) {
            JsonNode name = hub ? result.path("name") : result;
            if (name.isTextual() && ImageUtils.isSemver(name.asText())) best = higherTag(best, name.asText());
        }
        return best;
    }
}
