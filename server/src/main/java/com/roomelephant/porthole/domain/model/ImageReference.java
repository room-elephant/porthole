package com.roomelephant.porthole.domain.model;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A named Docker image reference. Registry identity is independent of HTTP transport configuration. */
@NullMarked
public record ImageReference(
        String registry,
        String repository,
        @Nullable String tag,
        @Nullable String digest) {
    private static final Set<String> HUB = Set.of("docker.io", "index.docker.io", "registry-1.docker.io");
    private static final String COMPONENT = "[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*";
    private static final Pattern REPOSITORY = Pattern.compile(COMPONENT + "(?:/" + COMPONENT + ")*");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern DIGEST = Pattern.compile("(?:sha256:[a-f0-9]{64}|sha512:[a-f0-9]{128})");

    public static Optional<ImageReference> tryParse(@Nullable String input) {
        if (input == null
                || input.isBlank()
                || !input.equals(input.strip())
                || input.matches("(?:sha256:)?[a-f0-9]{64}")) {
            return Optional.empty();
        }
        String[] parts = input.split("@", -1);
        if (parts.length > 2 || (parts.length == 2 && !DIGEST.matcher(parts[1]).matches())) return Optional.empty();
        String name = parts[0];
        String digest = parts.length == 2 ? parts[1] : null;
        String tag = null;
        int colon = name.lastIndexOf(':');
        if (colon > name.lastIndexOf('/')) {
            tag = name.substring(colon + 1);
            if (!TAG.matcher(tag).matches()) return Optional.empty();
            name = name.substring(0, colon);
        }
        String registry = "docker.io";
        int slash = name.indexOf('/');
        if (slash >= 0) {
            String prefix = name.substring(0, slash);
            if (prefix.equals("localhost")
                    || prefix.contains(".")
                    || prefix.contains(":")
                    || !prefix.equals(prefix.toLowerCase(Locale.ROOT))) {
                if (!validAuthority(prefix)) return Optional.empty();
                registry = prefix.toLowerCase(Locale.ROOT);
                name = name.substring(slash + 1);
            }
        }
        if (HUB.contains(registry)) registry = "docker.io";
        if (registry.equals("docker.io") && !name.contains("/")) name = "library/" + name;
        if (name.length() > 255 || !REPOSITORY.matcher(name).matches()) return Optional.empty();
        return Optional.of(new ImageReference(registry, name, tag, digest));
    }

    private static boolean validAuthority(String authority) {
        try {
            URI uri = URI.create("https://" + authority);
            return uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && uri.getRawPath().isEmpty()
                    && (uri.getPort() == -1 || (uri.getPort() > 0 && uri.getPort() <= 65535));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String canonicalRepository() {
        return registry + "/" + repository;
    }

    public String leafName() {
        return repository.substring(repository.lastIndexOf('/') + 1);
    }

    public @Nullable String lookupTag() {
        return digest != null ? null : tag == null ? "latest" : tag;
    }

    public boolean supportsUpdates() {
        return registry.equals("docker.io") && digest == null;
    }

    public List<String> matchingDigests(List<String> repoDigests) {
        return repoDigests.stream()
                .map(ImageReference::tryParse)
                .flatMap(Optional::stream)
                .filter(ref -> ref.canonicalRepository().equals(canonicalRepository()) && ref.digest != null)
                .map(ref -> ref.digest)
                .distinct()
                .toList();
    }
}
