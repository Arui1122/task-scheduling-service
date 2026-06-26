package com.example.demo.mq;

import com.example.demo.model.TaskMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")  // tests use CapturingTaskMessagePublisher instead
public class RocketMQTaskMessagePublisher implements TaskMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMQTaskMessagePublisher.class);

    private final DefaultMQProducer producer;
    private final ObjectMapper mapper;
    private final String topic;

    public RocketMQTaskMessagePublisher(DefaultMQProducer producer,
                                        ObjectMapper mapper,
                                        @Value("${rocketmq.topic}") String topic) {
        this.producer = producer;
        this.mapper = mapper;
        this.topic = topic;
    }

    @Override
    public void publish(TaskMessage message) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(message);
        } catch (Exception e) {
            throw new MessagePublishException("Failed to serialize TaskMessage", e);
        }
        Message rmqMessage = new Message(topic, "", message.taskId(), body);
        try {
            SendResult result = producer.send(rmqMessage);
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                throw new MessagePublishException(
                        "RocketMQ send returned non-OK status: " + result.getSendStatus(), null);
            }
            log.info("Published task {} to topic {}", message.taskId(), topic);
        } catch (MessagePublishException e) {
            throw e;
        } catch (Exception e) {
            throw new MessagePublishException("RocketMQ send failed for task " + message.taskId(), e);
        }
    }
}
