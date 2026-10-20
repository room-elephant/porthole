package com.roomelephant.porthole.config.properties;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds at the root "porthole" prefix, so it must keep ignoreUnknownFields at its default (true).
@NullMarked
@ConfigurationProperties("porthole")
@RegisterReflectionForBinding(SelfProperties.class)
public record SelfProperties(@Nullable String selfContainerId) {}
