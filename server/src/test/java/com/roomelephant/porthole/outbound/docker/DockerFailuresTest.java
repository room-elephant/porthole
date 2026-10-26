package com.roomelephant.porthole.outbound.docker;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DockerFailuresTest {
    @Test
    void disconnectedApacheTransportIsUnavailable() {
        assertThat(DockerFailures.isTransportFailure(new RuntimeException(
                        new com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.NoHttpResponseException(
                                "closed"))))
                .isTrue();
        assertThat(DockerFailures.isTransportFailure(new RuntimeException(
                        new com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.ConnectionClosedException(
                                "closed"))))
                .isTrue();
    }

    @Test
    void detectsEachSupportedConnectionFailureType() {
        for (Throwable cause : java.util.List.of(
                new java.net.SocketException(),
                new java.net.UnknownHostException(),
                new java.net.http.HttpTimeoutException("timeout"),
                new java.util.concurrent.TimeoutException(),
                new java.nio.channels.UnresolvedAddressException()))
            assertThat(DockerFailures.isTransportFailure(new RuntimeException(cause)))
                    .isTrue();
    }

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
        var b = new RuntimeException(a);
        a.initCause(b);
        assertThat(DockerFailures.isTransportFailure(a)).isFalse();
    }
}
