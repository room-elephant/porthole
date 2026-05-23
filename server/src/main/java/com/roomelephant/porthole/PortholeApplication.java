package com.roomelephant.porthole;

import com.roomelephant.porthole.config.ConfigurationPropertiesHints;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.roomelephant.porthole.config.properties")
@ImportRuntimeHints(ConfigurationPropertiesHints.class)
public class PortholeApplication {

    static void main(String[] args) {
        SpringApplication.run(PortholeApplication.class, args);
    }
}
