package com.roomelephant.porthole.core.port.outbound;

import java.util.List;
import org.jspecify.annotations.*;

/** Remote update evidence, with catalogue caching owned by the adapter. */
@NullMarked
public interface RegistryOperations {
    @Nullable
    String getLatestVersion(String imageName);

    boolean isTagChanged(String imageName, List<String> localDigests);

    void invalidateVersionCache();
}
