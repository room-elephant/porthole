package com.roomelephant.porthole.it.infra;

import com.github.dockerjava.api.DockerClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A GenericContainer that uses a specific Docker daemon (DinD) instead of the local one.
 */
public class DinDContainer<SELF extends DinDContainer<SELF>> extends GenericContainer<SELF> {
    public DinDContainer(DockerImageName dockerImageName, DockerClient dockerClient) {
        super(dockerImageName);
        this.dockerClient = dockerClient;
    }
}
