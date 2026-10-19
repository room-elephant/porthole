package com.roomelephant.porthole.outbound.registry.distribution;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RegistryProtocolException extends RuntimeException {
    public RegistryProtocolException(String safeReason) {
        super(safeReason);
    }

    public RegistryProtocolException(String safeReason, Throwable cause) {
        super(safeReason, cause);
    }
}
