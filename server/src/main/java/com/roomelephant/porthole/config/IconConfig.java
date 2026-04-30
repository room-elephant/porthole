package com.roomelephant.porthole.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.roomelephant.porthole.config.properties.DashboardProperties;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
@Slf4j
public class IconConfig {

    public static final String FILE_NAME = "icons.yml";

    @Bean
    public Map<String, String> iconMappings(DashboardProperties dashboardProperties) {
        Map<String, String> mappings = new HashMap<>();
        YAMLMapper yamlMapper = new YAMLMapper();

        try {
            ClassPathResource resource = new ClassPathResource(FILE_NAME);
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    Map<String, String> defaults = yamlMapper.readValue(inputStream, new TypeReference<>() {});
                    if (defaults != null) {
                        mappings.putAll(defaults);
                    }
                }
            }
        } catch (IOException _) {
            // Should not happen
        }

        File externalFile = new File(dashboardProperties.icons().path());
        if (externalFile.exists()) {
            try {
                Map<String, String> external = yamlMapper.readValue(externalFile, new TypeReference<>() {});
                if (external != null) {
                    mappings.putAll(external);
                }
                log.debug(
                        "Loaded external icon config from {}",
                        dashboardProperties.icons().path());
            } catch (IOException e) {
                log.error("Failed to load external icon config: {}", e.getMessage());
            }
        }

        log.debug("Icon config initialized with {} mappings", mappings.size());
        return Collections.unmodifiableMap(mappings);
    }
}
