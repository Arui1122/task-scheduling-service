package com.example.demo.mq;

import com.example.demo.domain.TaskMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMQTaskMessagePublisherTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private TaskMessage sample() {
        return new TaskMessage(
                "abc-123",
                Instant.parse("2030-01-01T00:00:00Z"),
                Instant.parse("2030-01-01T00:00:01Z"),
                Map.of("type", "email", "to", "x@y.com"));
    }

    @Test
    void publish_setsTopicAndKeysAndJsonBody() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        SendResult ok = new SendResult();
        ok.setSendStatus(SendStatus.SEND_OK);
        when(producer.send(org.mockito.ArgumentMatchers.any(Message.class))).thenReturn(ok);

        RocketMQTaskMessagePublisher pub = new RocketMQTaskMessagePublisher(producer, mapper, "task-schedule-topic");
        pub.publish(sample());

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(captor.capture());
        Message sent = captor.getValue();
        assertThat(sent.getTopic()).isEqualTo("task-schedule-topic");
        assertThat(sent.getKeys()).isEqualTo("abc-123");
        String body = new String(sent.getBody());
        assertThat(body).contains("\"taskId\":\"abc-123\"");
        assertThat(body).contains("\"type\":\"email\"");
    }

    @Test
    void publish_throwsMessagePublishException_whenSendStatusNotOk() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        SendResult bad = new SendResult();
        bad.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        when(producer.send(org.mockito.ArgumentMatchers.any(Message.class))).thenReturn(bad);

        RocketMQTaskMessagePublisher pub = new RocketMQTaskMessagePublisher(producer, mapper, "task-schedule-topic");
        assertThatThrownBy(() -> pub.publish(sample()))
                .isInstanceOf(MessagePublishException.class)
                .hasMessageContaining("non-OK status");
    }

    @Test
    void publish_wrapsProducerException() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        when(producer.send(org.mockito.ArgumentMatchers.any(Message.class)))
                .thenThrow(new RuntimeException("boom"));

        RocketMQTaskMessagePublisher pub = new RocketMQTaskMessagePublisher(producer, mapper, "task-schedule-topic");
        assertThatThrownBy(() -> pub.publish(sample()))
                .isInstanceOf(MessagePublishException.class)
                .hasMessageContaining("RocketMQ send failed");
    }
}
