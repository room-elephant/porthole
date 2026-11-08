package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.properties.*;
import com.roomelephant.porthole.core.model.SelfContainer;
import com.roomelephant.porthole.outbound.runtime.SelfContainerResolver;
import java.nio.file.Path;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class CoreConfig {
    private static final Logger log = LoggerFactory.getLogger(CoreConfig.class);

    @Bean
    @NonNull
    SelfContainer selfContainer(@NonNull SelfProperties p) {
        SelfContainer self = new SelfContainerResolver(
                        System::getenv, Path.of("/proc/self/mountinfo"), Path.of("/proc/self/cgroup"))
                .resolve(p.selfContainerId());
        log.info("Resolved own container ID: {}", self.id() == null ? "none" : self.id());
        return self;
    }
}
