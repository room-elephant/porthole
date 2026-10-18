package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.properties.*;
import com.roomelephant.porthole.core.enrichment.endpoint.EndpointSettings;
import com.roomelephant.porthole.core.presentation.icon.IconSettings;
import com.roomelephant.porthole.core.version.VersionRefreshSettings;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class CoreConfig {
    @Bean
    VersionRefreshSettings versionRefreshSettings(VersionProperties p) {
        return new VersionRefreshSettings(p.refreshInterval());
    }

    @Bean
    EndpointSettings endpointSettings(EndpointProperties p) {
        return new EndpointSettings(p.publishedHost());
    }

    @Bean
    IconSettings iconSettings(DashboardProperties p) {
        return new IconSettings(p.icons().url(), p.icons().extension());
    }
}
