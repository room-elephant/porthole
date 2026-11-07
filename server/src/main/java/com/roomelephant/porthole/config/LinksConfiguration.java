package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.nativehints.LinksRuntimeHints;
import com.roomelephant.porthole.config.properties.PreferencesProperties;
import com.roomelephant.porthole.core.model.ExternalLink;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.port.outbound.ExternalLinkStore;
import com.roomelephant.porthole.outbound.links.JsonExternalLinkStore;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding({ExternalLink.class, SelectedIcon.class})
@ImportRuntimeHints(LinksRuntimeHints.class)
public class LinksConfiguration {
    @Bean
    public ExternalLinkStore externalLinkStore(PreferencesProperties properties, JsonMapper mapper) {
        return new JsonExternalLinkStore(
                properties.path().toAbsolutePath().normalize().resolveSibling("links.json"), mapper);
    }
}
