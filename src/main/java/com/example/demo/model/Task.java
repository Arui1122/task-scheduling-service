package com.example.demo.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "tasks")
@EntityListeners(AuditingEntityListener.class)
public class Task {

    @Id
    @Column(name = "task_id", length = 64, nullable = false, updatable = false)
    private String taskId;

    @Column(name = "execute_at", nullable = false)
    private Instant executeAt;

    @Column(name = "payload", nullable = false)
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private TaskStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    protected Task() { /* JPA */ }

    public Task(String taskId, Instant executeAt, Map<String, Object> payload, TaskStatus status) {
        this.taskId = taskId;
        this.executeAt = executeAt;
        this.payload = payload;
        this.status = status;
    }

    public String getTaskId() { return taskId; }
    public Instant getExecuteAt() { return executeAt; }
    public Map<String, Object> getPayload() { return payload; }
    public TaskStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getTriggeredAt() { return triggeredAt; }

    public void setStatus(TaskStatus status) { this.status = status; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }

    /**
     * Stores a Map as a JSON string. H2 in MySQL mode tolerates VARCHAR/CLOB
     * here; production MySQL 8 maps `JSON` natively. ObjectMapper is held
     * statically because the converter is constructed by JPA without DI.
     */
    @Converter(autoApply = false)
    public static class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final TypeReference<Map<String, Object>> TYPE =
                new TypeReference<Map<String, Object>>() {};

        @Override
        public String convertToDatabaseColumn(Map<String, Object> attribute) {
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to serialize payload to JSON", e);
            }
        }

        @Override
        public Map<String, Object> convertToEntityAttribute(String dbData) {
            if (dbData == null) return null;
            try {
                return MAPPER.readValue(dbData, TYPE);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to deserialize payload from JSON", e);
            }
        }
    }
}
