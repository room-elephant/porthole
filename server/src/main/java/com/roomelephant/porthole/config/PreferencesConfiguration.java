package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.properties.PreferencesProperties;
import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import com.roomelephant.porthole.core.port.outbound.IconFileStore;
import com.roomelephant.porthole.core.service.ContainerIconService;
import com.roomelephant.porthole.outbound.icons.FilesystemIconFileStore;
import com.roomelephant.porthole.outbound.preferences.JsonContainerPreferencesStore;
import com.roomelephant.porthole.outbound.preferences.StoredContainerPreferences;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding({
    StoredContainerPreferences.class,
    ContainerPreferences.class,
    ContainerPreferencesKey.class,
    SelectedEndpoint.class,
    SelectedIcon.class
})
public class PreferencesConfiguration {
    @Bean
    public ContainerPreferencesStore containerPreferencesStore(PreferencesProperties properties, JsonMapper mapper) {
        return new JsonContainerPreferencesStore(properties.path(), mapper);
    }

    @Bean
    public IconFileStore iconFileStore(PreferencesProperties properties) {
        return new FilesystemIconFileStore(
                properties.path().toAbsolutePath().normalize().resolveSibling("icons"));
    }

    @Bean
    public ApplicationRunner unreferencedIconCleanup(ContainerIconService icons) {
        return _ -> icons.removeUnreferencedUploads();
    }
}
