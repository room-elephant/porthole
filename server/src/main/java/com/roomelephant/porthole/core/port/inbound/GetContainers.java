package com.roomelephant.porthole.core.port.inbound;

import com.roomelephant.porthole.core.model.*;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface GetContainers {
    ContainerInventory getContainers(ContainerQuery query);
}
