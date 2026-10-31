package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.properties.PreferencesProperties;
import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import com.roomelephant.porthole.outbound.preferences.JsonContainerPreferencesStore;
import com.roomelephant.porthole.outbound.preferences.StoredContainerPreferences;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding({
    StoredContainerPreferences.class,
    ContainerPreferences.class,
    ContainerPreferencesKey.class,
    SelectedEndpoint.class
})
public class PreferencesConfiguration {
    @Bean
    public ContainerPreferencesStore containerPreferencesStore(PreferencesProperties properties, JsonMapper mapper) {
        return new JsonContainerPreferencesStore(properties.path(), mapper);
    }
}
