package com.roomelephant.porthole.core.port.inbound;

import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface SelectContainerIcon {
    void selectLibraryIcon(ContainerPreferencesKey key, String name);

    void uploadIcon(ContainerPreferencesKey key, byte[] bytes);
}
