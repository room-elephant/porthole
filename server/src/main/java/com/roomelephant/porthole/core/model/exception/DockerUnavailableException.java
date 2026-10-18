package com.roomelephant.porthole.core.model.exception;

public class DockerUnavailableException extends RuntimeException {
    public DockerUnavailableException(Exception e) {
        super(e);
    }
}
