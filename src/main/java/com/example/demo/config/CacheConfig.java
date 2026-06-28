package com.example.demo.config;

import com.example.demo.controller.dto.TaskResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Spring Cache wiring for the task read path.
 *
 * <p>{@code @EnableCaching} is always active so the {@code @Cacheable} /
 * {@code @CacheEvict} annotations on {@link com.example.demo.service.TaskService}
 * take effect in every profile. The Redis-backed cache manager is only
 * created outside the test profile (mirroring {@code RocketMQConfig}); under
 * {@code test} no CacheManager bean is defined here, so Spring Boot's
 * auto-configuration provides a simple in-memory one
 * ({@code spring.cache.type=simple}) and the suite needs no real Redis.
 *
 * <p>This is the second, distinct use of Redis in the service: the ZSet in
 * {@code RedisDelayQueue} is the delay-queue driver; this is a read-through
 * cache for {@code GET /tasks/{id}}, which is the polling hot-read in this
 * domain (clients poll a task's status until it flips to TRIGGERED).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Single cache region holding TaskResponse DTOs keyed by taskId. */
    public static final String TASKS_CACHE = "tasks";

    /**
     * Redis cache manager (production / any non-test profile).
     *
     * <p>Values are serialized as JSON bound to {@link TaskResponse} — we
     * cache the immutable DTO, never the JPA entity, to avoid leaking lazy
     * proxies or the {@code @Version} field into the cache. A short TTL is
     * the backstop: even if an eviction is ever missed, a stale entry can
     * only live for {@code cache.task.ttl-seconds}.
     */
    /**
     * ObjectMapper for cached values. Kept separate from the HTTP layer's
     * auto-configured mapper, but deliberately matched to it: Instants are
     * written as ISO-8601 strings (not epoch timestamps) so the cached JSON
     * mirrors the API response and stays readable when inspecting Redis
     * directly. Package-visible so {@code CacheConfigTest} can pin the shape.
     */
    static ObjectMapper cacheObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    @Profile("!test")
    public RedisCacheManager taskCacheManager(RedisConnectionFactory connectionFactory,
                                              @Value("${cache.task.ttl-seconds:60}") long ttlSeconds) {
        Jackson2JsonRedisSerializer<TaskResponse> valueSerializer =
                new Jackson2JsonRedisSerializer<>(cacheObjectMapper(), TaskResponse.class);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
