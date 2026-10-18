package com.roomelephant.porthole.core.model.exception;

public class UnexpectedException extends RuntimeException {
    public UnexpectedException(Exception e) {
        super(e);
    }
}
