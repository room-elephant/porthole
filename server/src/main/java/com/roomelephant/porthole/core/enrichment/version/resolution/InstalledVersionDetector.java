package com.roomelephant.porthole.core.enrichment.version.resolution;

import com.roomelephant.porthole.core.model.*;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.*;

@NullMarked
public final class InstalledVersionDetector {
    private static final String LABEL_OCI_IMAGE_VERSION = "org.opencontainers.image.version";
    private static final String LABEL_IMAGE_VERSION = "version";

    public @Nullable String detect(ContainerInspection inspection) {
        String imageFull = inspection.image();
        if (imageFull == null) return null;
        String imageName = ImageUtils.extractName(imageFull);

        String envVersion = getVersionFromEnvVars(inspection.environment(), imageName);
        if (envVersion != null) {
            return envVersion;
        }

        String labelVersion = getVersionFromLabels(inspection.labels());
        if (labelVersion != null) {
            return labelVersion;
        }

        return ImageReference.tryParse(imageFull)
                .map(ref -> ref.tag() != null ? ref.tag() : ref.lookupTag())
                .orElse(null);
    }

    private @Nullable String getVersionFromEnvVars(List<String> envs, String imageName) {

        String targetEnv = imageName.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "_") + "_VERSION=";
        String generic = null;
        for (String env : envs) {
            if (env.startsWith(targetEnv)) {
                String value = nonBlank(env.substring(targetEnv.length()));
                if (value != null) return value;
            }
            if (env.startsWith("VERSION=") && generic == null) generic = nonBlank(env.substring("VERSION=".length()));
        }
        return generic;
    }

    private @Nullable String nonBlank(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private @Nullable String getVersionFromLabels(@Nullable Map<String, String> labels) {
        if (labels == null) {
            return null;
        }
        String ociVersion = nonBlank(labels.get(LABEL_OCI_IMAGE_VERSION));
        return ociVersion != null ? ociVersion : nonBlank(labels.get(LABEL_IMAGE_VERSION));
    }
}
