package com.roomelephant.porthole;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.roomelephant.porthole.config.properties")
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Porthole API",
                        version = "${spring.application.version}",
                        description =
                                "Discover Docker containers, their service endpoints, and cached image version updates."),
        servers = @Server(url = "/", description = "Current Porthole instance"))
public class PortholeApplication {

    static void main(String[] args) {
        SpringApplication.run(PortholeApplication.class, args);
    }
}
