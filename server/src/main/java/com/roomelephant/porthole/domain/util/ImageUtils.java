package com.roomelephant.porthole.domain.util;

import com.roomelephant.porthole.domain.model.ImageReference;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class ImageUtils {

    public static final String UNKNOWN_IMAGE_NAME = "Unknown";
    public static final Pattern SEMVER_PATTERN = Pattern.compile("^v?\\d++(\\.\\d++)++$");
    private static final String LATEST = "latest";

    private ImageUtils() {}

    /**
     * Extracts the tag from a Docker image reference.
     * Example: "nginx:1.25" → "1.25", "redis" → "latest"
     */
    public static @NonNull String extractTag(@NonNull String image) {
        return ImageReference.tryParse(image).map(ImageReference::lookupTag).orElse(LATEST);
    }

    /**
     * Extracts the image name without registry prefix or tag.
     * Example: "my-reg/nginx:latest" → "nginx", "postgres:15" → "postgres"
     */
    public static @NonNull String extractName(@Nullable String image) {
        return ImageReference.tryParse(image).map(ImageReference::leafName).orElse(UNKNOWN_IMAGE_NAME);
    }

    /**
     * Resolves the full repository path for Docker Hub API calls.
     * Official images get the "library/" prefix.
     * Example: "redis" → "library/redis", "bitnami/redis:7" → "bitnami/redis"
     */
    public static @NonNull String resolveRepository(@NonNull String image) {
        return ImageReference.tryParse(image)
                .filter(ImageReference::supportsUpdates)
                .map(ImageReference::repository)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported image reference"));
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
