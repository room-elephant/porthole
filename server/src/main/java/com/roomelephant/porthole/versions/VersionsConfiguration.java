package com.roomelephant.porthole.versions;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@NullMarked
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VersionProperties.class)
public class VersionsConfiguration {}
