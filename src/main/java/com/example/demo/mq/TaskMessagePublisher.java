package com.example.demo.mq;

import com.example.demo.model.TaskMessage;

public interface TaskMessagePublisher {
    /**
     * Synchronously publish a task message. Throws on any failure; the
     * caller (scheduler) treats this as a signal to revert the DB state
     * for retry on the next tick.
     */
    void publish(TaskMessage message);
}
