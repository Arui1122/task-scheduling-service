package com.example.demo.controller.dto;

import com.example.demo.model.Task;
import com.example.demo.model.TaskStatus;

import java.time.Instant;
import java.util.Map;

public record TaskResponse(
        String taskId,
        Instant executeAt,
        Map<String, Object> payload,
        TaskStatus status,
        Instant createdAt,
        Instant triggeredAt
) {
    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.getTaskId(),
                t.getExecuteAt(),
                t.getPayload(),
                t.getStatus(),
                t.getCreatedAt(),
                t.getTriggeredAt());
    }
}
