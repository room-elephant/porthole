package com.roomelephant.porthole.entries.store;

import com.roomelephant.porthole.entries.EntriesProperties;
import com.roomelephant.porthole.entries.StateStore;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
@Configuration(proxyBeanMethods = false)
public class StoreConfiguration {
    static final String STATE_FILE = "state.json";

    @Bean
    StateStore stateStore(EntriesProperties properties, JsonMapper mapper) {
        return new JsonStateStore(properties.dir().resolve(STATE_FILE), mapper);
    }
}
