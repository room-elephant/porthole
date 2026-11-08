package com.roomelephant.porthole.fleet;

import org.jspecify.annotations.NullMarked;

/** The container no longer exists on its source. */
@NullMarked
public class ContainerNotFoundException extends RuntimeException {
    public ContainerNotFoundException(String containerId) {
        super("Container not found: " + containerId);
    }
}
