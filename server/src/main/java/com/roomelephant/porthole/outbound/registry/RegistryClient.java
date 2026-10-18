package com.roomelephant.porthole.outbound.registry;

import com.roomelephant.porthole.core.model.ImageReference;
import java.util.List;
import org.jspecify.annotations.*;

/** Provider contract internal to the registry adapter. */
@NullMarked
public interface RegistryClient {
    boolean supports(ImageReference image);

    @Nullable
    String getLatestVersion(ImageReference image);

    boolean isTagChanged(ImageReference image, List<String> localDigests);
}
