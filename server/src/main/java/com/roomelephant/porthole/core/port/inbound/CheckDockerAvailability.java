package com.roomelephant.porthole.core.port.inbound;

import com.roomelephant.porthole.core.model.DockerAvailability;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface CheckDockerAvailability {
    DockerAvailability checkAvailability();
}
