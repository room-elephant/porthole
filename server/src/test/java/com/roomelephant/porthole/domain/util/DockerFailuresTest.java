package com.roomelephant.porthole.domain.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DockerFailuresTest {
    @Test
    void detectsNestedTimeoutsAndConnectionFailures() {
        assertThat(DockerFailures.isTransportFailure(
                        new RuntimeException(new RuntimeException(new java.net.SocketTimeoutException()))))
                .isTrue();
        assertThat(DockerFailures.isTransportFailure(new IllegalArgumentException("bad metadata")))
                .isFalse();
    }

    @Test
    void causeCycleDoesNotHangClassification() {
        var a = new RuntimeException();
        var b = new RuntimeException();
        a.initCause(b);
        b.initCause(a);
        assertThat(DockerFailures.isTransportFailure(a)).isFalse();
    }
}
