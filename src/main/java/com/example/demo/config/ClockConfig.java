package com.example.demo.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /**
     * Single source of "now" for the entire application. Tests override
     * this bean with Clock.fixed(...) to make time-dependent assertions
     * deterministic.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
