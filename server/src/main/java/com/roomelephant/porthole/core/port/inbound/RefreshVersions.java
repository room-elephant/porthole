package com.roomelephant.porthole.core.port.inbound;
/** Queues background checks without performing remote I/O on the caller. */
public interface RefreshVersions {
    void requestRefresh();
}
