package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Locale;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        // Force English Bean Validation messages regardless of host JVM locale.
        // Without this, validation errors render in whatever the JVM defaults to
        // (e.g., "必須小於或等於 100" on a zh-TW host), which is bad UX for an
        // English-language API.
        Locale.setDefault(Locale.ENGLISH);
        SpringApplication.run(DemoApplication.class, args);
    }
}
