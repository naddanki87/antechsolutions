package com.antech.tasktracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI taskTrackerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Task Tracker API")
                .version("1.0.0")
                .description("Antech Solutions task tracker. Errors use RFC 7807 application/problem+json."));
    }
}
