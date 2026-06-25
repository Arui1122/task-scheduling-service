package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

/**
 * Wires Spring Data JPA Auditing to the injected Clock so that
 * @CreatedDate / @LastModifiedDate timestamps come from the same time
 * source the rest of the application uses (fixed in tests via
 * Clock.fixed(...) substitution).
 *
 * Keeping @EnableJpaAuditing here (not on DemoApplication) ensures that
 * @WebMvcTest slices, which exclude this class, are not affected by the
 * JPA auditing infrastructure. @DataJpaTest tests that need auditing must
 * explicitly import this class (and ClockConfig) via @Import.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
