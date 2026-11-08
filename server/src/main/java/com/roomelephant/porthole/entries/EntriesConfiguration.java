package com.roomelephant.porthole.entries;

import com.roomelephant.porthole.entries.icons.FilesystemIconFileStore;
import com.roomelephant.porthole.entries.store.JsonStateStore;
import java.time.Clock;
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

    static final String ICONS_DIRECTORY = "icons";

    @Bean
    StateStore stateStore(EntriesProperties properties, JsonMapper mapper) {
        return new JsonStateStore(properties.dir().resolve(STATE_FILE), mapper);
    }

    @Bean
    IconFileStore entryIconFileStore(EntriesProperties properties) {
        return new FilesystemIconFileStore(properties.dir().resolve(ICONS_DIRECTORY), Clock.systemUTC());
    }
}
