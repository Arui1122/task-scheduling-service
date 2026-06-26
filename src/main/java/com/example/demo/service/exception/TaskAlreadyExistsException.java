package com.example.demo.service.exception;

public class TaskAlreadyExistsException extends RuntimeException {
    public TaskAlreadyExistsException(String taskId) {
        super("Task already exists: " + taskId);
    }
}
