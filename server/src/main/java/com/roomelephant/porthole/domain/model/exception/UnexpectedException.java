package com.roomelephant.porthole.domain.model.exception;

public class UnexpectedException extends RuntimeException {
    public UnexpectedException(Exception e) {
        super(e);
    }
}
