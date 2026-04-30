package com.roomelephant.porthole.domain.model.exception;

public class DockerUnavailableException extends RuntimeException {
    public DockerUnavailableException(Exception e) {
        super(e);
    }
}
