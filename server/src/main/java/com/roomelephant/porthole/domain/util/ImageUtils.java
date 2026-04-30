package com.roomelephant.porthole.domain.util;

import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class ImageUtils {

    public static final String UNKNOWN_IMAGE_NAME = "Unknown";
    public static final Pattern SEMVER_PATTERN = Pattern.compile("^v?\\d++(\\.\\d++)++$");
    private static final String LIBRARY = "library/";
    private static final String LATEST = "latest";

    private ImageUtils() {}

    /**
     * Extracts the tag from a Docker image reference.
     * Example: "nginx:1.25" → "1.25", "redis" → "latest"
     */
    public static @NonNull String extractTag(@NonNull String image) {
        int colonIndex = image.lastIndexOf(":");
        return colonIndex != -1 ? image.substring(colonIndex + 1) : LATEST;
    }

    /**
     * Extracts the image name without registry prefix or tag.
     * Example: "my-reg/nginx:latest" → "nginx", "postgres:15" → "postgres"
     */
    public static @NonNull String extractName(@Nullable String image) {
        if (image == null) {
            return UNKNOWN_IMAGE_NAME;
        }

        String name = image;
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf("/") + 1);
        }
        if (name.contains(":")) {
            name = name.substring(0, name.indexOf(":"));
        }
        return name;
    }

    /**
     * Resolves the full repository path for Docker Hub API calls.
     * Official images get the "library/" prefix.
     * Example: "redis" → "library/redis", "bitnami/redis:7" → "bitnami/redis"
     */
    public static @NonNull String resolveRepository(@NonNull String image) {
        String repo = image;
        // Strip tag if present (colon after the last slash)
        int lastColon = repo.lastIndexOf(':');
        int lastSlash = repo.lastIndexOf('/');
        if (lastColon > lastSlash) {
            repo = repo.substring(0, lastColon);
        }

        // Strip registry host if present
        int firstSlash = repo.indexOf('/');
        if (firstSlash != -1) {
            String prefix = repo.substring(0, firstSlash);
            if (prefix.contains(".") || prefix.contains(":") || prefix.equals("localhost")) {
                repo = repo.substring(firstSlash + 1);
            }
        }

        // Add library prefix for official images
        return repo.contains("/") ? repo : LIBRARY + repo;
    }

    /**
     * Checks if a tag follows semantic versioning (e.g., "1.0", "v2.3.4").
     */
    public static boolean isSemver(@NonNull String tag) {
        return SEMVER_PATTERN.matcher(tag).matches();
    }

    /**
     * Compares two semantic version strings.
     * Returns negative if v1 < v2, positive if v1 > v2, zero if equal.
     */
    public static int compareSemVer(@NonNull String v1, @NonNull String v2) {
        String[] parts1 = v1.replaceFirst("^v", "").split("\\.");
        String[] parts2 = v2.replaceFirst("^v", "").split("\\.");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        return 0;
    }
}
