package com.roomelephant.porthole.entries.icons;

import com.roomelephant.porthole.entries.EntriesProperties;
import com.roomelephant.porthole.entries.IconFileStore;
import java.time.Clock;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@NullMarked
@Configuration(proxyBeanMethods = false)
public class IconsConfiguration {
    static final String ICONS_DIRECTORY = "icons";

    @Bean
    IconFileStore iconFileStore(EntriesProperties properties) {
        return new FilesystemIconFileStore(properties.dir().resolve(ICONS_DIRECTORY), Clock.systemUTC());
    }
}
