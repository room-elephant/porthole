package com.roomelephant.porthole.it.infra;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class DockerConfiguration {

    @Bean(destroyMethod = "close")
    DockerInfrastructure dockerInfrastructure() {
        return new DockerInfrastructure();
    }
}
