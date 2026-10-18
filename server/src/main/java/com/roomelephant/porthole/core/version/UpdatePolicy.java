package com.roomelephant.porthole.core.version;

import com.roomelephant.porthole.core.model.*;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.*;

@NullMarked
public final class UpdatePolicy {
    public enum Evidence {
        POSITIVE,
        NEGATIVE,
        INCONCLUSIVE,
        INAPPLICABLE
    }

    public enum Decision {
        UPDATE_AVAILABLE,
        NO_UPDATE,
        INCONCLUSIVE
    }

    public Evidence release(ImageReference image, @Nullable String current, RegistryLookup<TagCatalogue> catalogue) {
        if (NumericVersion.tryParse(image.lookupTag()).isEmpty()) return Evidence.INAPPLICABLE;
        if (!(catalogue instanceof RegistryLookup.Available<TagCatalogue> available)) return Evidence.INCONCLUSIVE;
        String latest = new ReleasePolicy().latest(available.value());
        if (latest == null) return Evidence.NEGATIVE;
        Optional<NumericVersion> installed = NumericVersion.tryParse(current);
        if (installed.isEmpty()) return Evidence.INCONCLUSIVE;
        return NumericVersion.tryParse(latest).orElseThrow().compareTo(installed.get()) > 0
                ? Evidence.POSITIVE
                : Evidence.NEGATIVE;
    }

    public Evidence manifest(List<String> installedDigests, ManifestEvidence evidence) {
        if (!(evidence.tagged() instanceof RegistryLookup.Available<ManifestMetadata> tagged))
            return Evidence.INCONCLUSIVE;
        ManifestMetadata remote = tagged.value();
        if (remote.kind() == ManifestMetadata.Kind.UNKNOWN) return Evidence.INCONCLUSIVE;
        if (installedDigests.contains(remote.digest())) return Evidence.NEGATIVE;
        for (String digest : installedDigests) {
            if (!digest.split(":", 2)[0].equals(remote.digest().split(":", 2)[0])) continue;
            if (evidence.installed().get(digest) instanceof RegistryLookup.Available<ManifestMetadata> local
                    && local.value().digest().equals(digest)
                    && local.value().kind() == remote.kind()) return Evidence.POSITIVE;
        }
        return Evidence.INCONCLUSIVE;
    }

    public Decision combine(Evidence release, Evidence manifest) {
        if (release == Evidence.POSITIVE || manifest == Evidence.POSITIVE) return Decision.UPDATE_AVAILABLE;
        if (release == Evidence.INCONCLUSIVE || manifest == Evidence.INCONCLUSIVE) return Decision.INCONCLUSIVE;
        return Decision.NO_UPDATE;
    }
}
