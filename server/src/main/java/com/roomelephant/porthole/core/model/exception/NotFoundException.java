package com.roomelephant.porthole.core.model.exception;

public class NotFoundException extends RuntimeException {
    private final String containerId;

    public NotFoundException(String containerId) {
        this.containerId = containerId;
    }

    public String getContainerId() {
        return containerId;
    }
}
