package com.roomelephant.porthole.config.properties;

import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@NullMarked
@ConfigurationProperties("porthole.preferences")
@RegisterReflectionForBinding(PreferencesProperties.class)
public record PreferencesProperties(
        @DefaultValue("data/preferences.json") Path path) {}
