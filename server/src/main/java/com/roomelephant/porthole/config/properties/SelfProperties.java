package com.roomelephant.porthole.config.properties;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;

@NullMarked
@ConfigurationProperties("porthole")
@RegisterReflectionForBinding(SelfProperties.class)
public record SelfProperties(@Nullable String selfContainerId) {}
