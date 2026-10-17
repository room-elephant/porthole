package com.roomelephant.porthole.domain.util;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeoutException;

public final class DockerFailures {
    private DockerFailures() {}

    public static boolean isTransportFailure(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable e = error; e != null && seen.add(e); e = e.getCause()) {
            if (e instanceof com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.NoHttpResponseException
                    || e
                            instanceof
                            com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.ConnectionClosedException
                    || e instanceof SocketException
                    || e instanceof SocketTimeoutException
                    || e instanceof UnknownHostException
                    || e instanceof java.net.http.HttpTimeoutException
                    || e instanceof TimeoutException
                    || e instanceof java.nio.channels.UnresolvedAddressException) return true;
        }
        return false;
    }
}
