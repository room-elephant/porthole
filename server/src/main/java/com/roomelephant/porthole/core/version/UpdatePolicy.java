package com.roomelephant.porthole.core.version;

import com.roomelephant.porthole.core.model.ImageUtils;
import java.util.Optional;
import org.jspecify.annotations.*;

@NullMarked
public final class UpdatePolicy {
    public boolean updateAvailable(
            String imageName, @Nullable String currentVersion, @Nullable String latestVersion, boolean tagChanged) {
        if (tagChanged) return true;
        Optional<NumericVersion> current = NumericVersion.tryParse(currentVersion);
        Optional<NumericVersion> latest = NumericVersion.tryParse(latestVersion);
        return ImageUtils.isSemver(ImageUtils.extractTag(imageName))
                && current.isPresent()
                && latest.isPresent()
                && latest.get().compareTo(current.get()) > 0;
    }
}
