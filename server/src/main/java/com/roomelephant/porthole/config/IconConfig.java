package com.roomelephant.porthole.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.roomelephant.porthole.config.properties.DashboardProperties;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

@Configuration
@Slf4j
public class IconConfig {

    public static final String FILE_NAME = "icons.yml";

    @Bean
    public Map<String, String> iconMappings(DashboardProperties properties) {
        var mappings = new HashMap<>(loadMappings(new ClassPathResource(FILE_NAME)));
        mappings.putAll(loadMappings(new FileSystemResource(properties.icons().path())));

        log.debug("Icon config initialized with {} mappings", mappings.size());
        return Collections.unmodifiableMap(mappings);
    }

    private Map<String, String> loadMappings(Resource resource) {
        if (!resource.exists()) return Map.of();

        try (var input = resource.getInputStream()) {
            Map<String, String> mappings = new YAMLMapper().readValue(input, new TypeReference<>() {});
            return mappings == null ? Map.of() : mappings;
        } catch (IOException e) {
            log.error("Failed to load icon config from {}: {}", resource, e.getMessage());
            return Map.of();
        }
    }
}
