package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@NullMarked
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EntriesProperties.class)
public class EntriesConfiguration {}
