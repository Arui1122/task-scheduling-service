package com.example.demo.config;

import com.example.demo.controller.dto.TaskResponse;
import com.example.demo.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON shape of cached values. The cache uses its own ObjectMapper
 * (separate from the HTTP layer's auto-configured one), so without an explicit
 * assertion it silently drifts — Instants were being written as epoch numbers
 * while the API emits ISO-8601 strings. We want the cached representation to
 * match the API and stay human-readable when inspecting Redis directly.
 */
class CacheConfigTest {

    @Test
    void cacheObjectMapper_writesInstantsAsIsoStrings_notEpochNumbers() throws Exception {
        ObjectMapper mapper = CacheConfig.cacheObjectMapper();

        TaskResponse response = new TaskResponse(
                "abc-123",
                Instant.parse("2030-01-01T00:00:00Z"),
                Map.of("type", "email"),
                TaskStatus.PENDING,
                Instant.parse("2026-06-25T10:00:00Z"),
                null);

        String json = mapper.writeValueAsString(response);

        assertThat(json).contains("\"executeAt\":\"2030-01-01T00:00:00Z\"");
        assertThat(json).contains("\"createdAt\":\"2026-06-25T10:00:00Z\"");
        assertThat(json).doesNotContain("1.8");  // no epoch-second float like 1893456000.0
    }

    @Test
    void cacheObjectMapper_roundTripsTaskResponse() throws Exception {
        ObjectMapper mapper = CacheConfig.cacheObjectMapper();

        TaskResponse original = new TaskResponse(
                "abc-123",
                Instant.parse("2030-01-01T00:00:00Z"),
                Map.of("type", "email", "target", "x@y.com"),
                TaskStatus.TRIGGERED,
                Instant.parse("2026-06-25T10:00:00Z"),
                Instant.parse("2030-01-01T00:00:01Z"));

        TaskResponse restored = mapper.readValue(mapper.writeValueAsString(original), TaskResponse.class);

        assertThat(restored).isEqualTo(original);
    }
}
