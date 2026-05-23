package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.properties.DashboardProperties;
import com.roomelephant.porthole.config.properties.DockerProperties;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class ConfigurationPropertiesHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        MemberCategory[] fullAccess = {
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        };
        hints.reflection()
                .registerType(DashboardProperties.class, fullAccess)
                .registerType(DashboardProperties.Icons.class, fullAccess)
                .registerType(DockerProperties.class, fullAccess)
                .registerType(RegistryProperties.class, fullAccess)
                .registerType(RegistryProperties.Urls.class, fullAccess)
                .registerType(RegistryProperties.Timeout.class, fullAccess)
                .registerType(RegistryProperties.Cache.class, fullAccess);
    }
}
