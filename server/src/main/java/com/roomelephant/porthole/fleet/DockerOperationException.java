package com.roomelephant.porthole.fleet;

import org.jspecify.annotations.NullMarked;

/** Docker answered, but the operation failed. */
@NullMarked
public class DockerOperationException extends RuntimeException {
    public DockerOperationException(Exception e) {
        super(e);
    }
}
