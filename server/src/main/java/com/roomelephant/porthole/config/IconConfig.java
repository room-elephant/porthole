package com.roomelephant.porthole.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.roomelephant.porthole.config.properties.DashboardProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
public class IconConfig {
    private static final Logger log = LoggerFactory.getLogger(IconConfig.class);

    public static final String FILE_NAME = "icons.yml";

    @Bean
    public @NonNull Map<String, String> iconMappings(@NonNull DashboardProperties properties) {
        Map<String, String> mappings = new HashMap<>(loadMappings(new ClassPathResource(FILE_NAME)));
        mappings.putAll(loadMappings(new FileSystemResource(properties.icons().path())));

        log.debug("Icon config initialized with {} mappings", mappings.size());
        return Collections.unmodifiableMap(mappings);
    }

    private @NonNull Map<String, String> loadMappings(@NonNull Resource resource) {
        if (!resource.exists()) return Map.of();

        try (InputStream input = resource.getInputStream()) {
            Map<String, String> mappings = new YAMLMapper().readValue(input, new TypeReference<>() {});
            return mappings == null ? Map.of() : mappings;
        } catch (IOException e) {
            log.error("Failed to load icon config from {}: {}", resource, e.getMessage());
            return Map.of();
        }
    }
}
