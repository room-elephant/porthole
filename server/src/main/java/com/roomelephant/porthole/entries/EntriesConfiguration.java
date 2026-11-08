package com.roomelephant.porthole.entries;

import com.roomelephant.porthole.entries.store.JsonStateStore;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EntriesProperties.class)
public class EntriesConfiguration {
    static final String STATE_FILE = "state.json";

    @Bean
    StateStore stateStore(EntriesProperties properties, JsonMapper mapper) {
        return new JsonStateStore(properties.dir().resolve(STATE_FILE), mapper);
    }
}
