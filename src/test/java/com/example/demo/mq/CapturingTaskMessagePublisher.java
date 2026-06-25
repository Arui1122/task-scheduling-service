package com.example.demo.mq;

import com.example.demo.domain.TaskMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records every TaskMessage sent. Used by the integration test instead
 * of touching a real RocketMQ broker.
 */
public class CapturingTaskMessagePublisher implements TaskMessagePublisher {

    private final List<TaskMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void publish(TaskMessage message) {
        sent.add(message);
    }

    public List<TaskMessage> getSent() {
        return List.copyOf(sent);
    }
}
