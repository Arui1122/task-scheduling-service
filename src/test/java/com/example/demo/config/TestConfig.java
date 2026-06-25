package com.example.demo.config;

import com.example.demo.mq.CapturingTaskMessagePublisher;
import com.example.demo.mq.TaskMessagePublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestConfig {

    @Bean
    public TaskMessagePublisher taskMessagePublisher() {
        return new CapturingTaskMessagePublisher();
    }
}

