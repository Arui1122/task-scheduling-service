package com.example.demo.model;

import java.time.Instant;
import java.util.Map;

/**
 * Payload published to RocketMQ when a task fires. Consumers must treat
 * delivery as at-least-once and dedupe by taskId.
 */
public record TaskMessage(
        String taskId,
        Instant executeAt,
        Instant publishedAt,
        Map<String, Object> payload
) {
}
