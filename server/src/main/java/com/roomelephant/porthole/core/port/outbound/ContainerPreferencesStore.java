package com.roomelephant.porthole.core.port.outbound;

import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface ContainerPreferencesStore {
    Map<ContainerPreferencesKey, ContainerPreferences> snapshot();

    ContainerPreferences replaceSelection(ContainerPreferencesKey key, @Nullable SelectedEndpoint selection);
}
