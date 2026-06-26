package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI taskSchedulingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Task Scheduling Service")
                .version("0.0.1")
                .description("RESTful task scheduler backed by MySQL, Redis ZSet, and RocketMQ."));
    }
}
