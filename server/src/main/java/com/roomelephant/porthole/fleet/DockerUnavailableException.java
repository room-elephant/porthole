package com.roomelephant.porthole.fleet;

import org.jspecify.annotations.NullMarked;

/** Docker could not be reached or did not answer in time. */
@NullMarked
public class DockerUnavailableException extends RuntimeException {
    public DockerUnavailableException(Exception e) {
        super(e);
    }
}
