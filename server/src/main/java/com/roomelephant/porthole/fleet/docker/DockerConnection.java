package com.roomelephant.porthole.fleet.docker;

import com.github.dockerjava.api.DockerClient;
import org.jspecify.annotations.NullMarked;

/** The client for one configured source. */
@NullMarked
public record DockerConnection(String id, DockerClient client) {}
