package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.properties.*;
import com.roomelephant.porthole.core.enrichment.icon.IconSettings;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshSettings;
import com.roomelephant.porthole.core.model.SelfContainer;
import com.roomelephant.porthole.outbound.runtime.SelfContainerResolver;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.*;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class CoreConfig {
    @Bean
    SelfContainer selfContainer(SelfProperties p) {
        SelfContainer self = new SelfContainerResolver(
                        System::getenv, Path.of("/proc/self/mountinfo"), Path.of("/proc/self/cgroup"))
                .resolve(p.selfContainerId());
        log.info("Resolved own container ID: {}", self.id() == null ? "none" : self.id());
        return self;
    }

    @Bean
    VersionRefreshSettings versionRefreshSettings(VersionProperties p) {
        return new VersionRefreshSettings(p.refreshInterval());
    }

    @Bean
    IconSettings iconSettings(DashboardProperties p) {
        return new IconSettings(p.icons().url(), p.icons().extension());
    }
}
