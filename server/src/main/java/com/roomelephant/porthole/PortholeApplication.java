package com.roomelephant.porthole;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.roomelephant.porthole.config.properties")
public class PortholeApplication {

    static void main(String[] args) {
        SpringApplication.run(PortholeApplication.class, args);
    }
}
