package com.roomelephant.porthole.core.port.inbound;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface SelectContainerEndpoint {
    ContainerPreferences select(ContainerPreferencesKey key, @Nullable SelectedEndpoint selection);
}
