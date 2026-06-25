package com.example.demo.web;

import com.example.demo.domain.Task;
import com.example.demo.domain.TaskStatus;
import com.example.demo.service.TaskService;
import com.example.demo.web.dto.CreateTaskRequest;
import com.example.demo.web.dto.TaskResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/tasks")
@Validated
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest req) {
        Task created = service.create(req.taskId(), req.executeAt(), req.payload());
        URI location = UriComponentsBuilder.fromPath("/tasks/{id}").buildAndExpand(created.getTaskId()).toUri();
        return ResponseEntity.created(location).body(TaskResponse.from(created));
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(@PathVariable String taskId) {
        return TaskResponse.from(service.get(taskId));
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false, defaultValue = "PENDING") String status,
            @RequestParam(required = false, defaultValue = "0") @Min(0) int page,
            @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        TaskStatus parsed;
        try {
            parsed = TaskStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid status: " + status);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "executeAt"));
        Page<Task> result = service.list(parsed, pageable);
        List<TaskResponse> content = result.getContent().stream().map(TaskResponse::from).toList();
        return Map.of(
                "content", content,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "totalElements", result.getTotalElements(),
                        "totalPages", result.getTotalPages()
                )
        );
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> cancel(@PathVariable String taskId) {
        service.cancel(taskId);
        return ResponseEntity.noContent().build();
    }
}
