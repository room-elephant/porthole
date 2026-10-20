package com.roomelephant.porthole.core.model.exception;

import lombok.Getter;

public class NotFoundException extends RuntimeException {
    @Getter
    private final String containerId;

    public NotFoundException(String containerId) {
        this.containerId = containerId;
    }
}
