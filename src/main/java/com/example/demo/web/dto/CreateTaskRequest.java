package com.example.demo.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public record CreateTaskRequest(
        @NotBlank @Size(max = 64) String taskId,
        @NotNull Instant executeAt,
        @NotNull Map<String, Object> payload
) {
}
