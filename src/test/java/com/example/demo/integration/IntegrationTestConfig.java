package com.example.demo.integration;

import com.example.demo.delayqueue.DelayQueue;
import com.example.demo.delayqueue.InMemoryDelayQueue;
import com.example.demo.mq.CapturingTaskMessagePublisher;
import com.example.demo.mq.TaskMessagePublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the production DelayQueue and TaskMessagePublisher with fakes.
 * Note: RocketMQConfig and RocketMQTaskMessagePublisher are both annotated
 * @Profile("!test") so they don't load under the test profile, and we
 * never need a real RocketMQ broker to run integration tests. The Redis
 * connection factory is auto-configured (Lettuce) but Lettuce connects
 * lazily — since DelayQueue below is @Primary, the Redis-backed one
 * exists in the context but is never invoked.
 */
@TestConfiguration
public class IntegrationTestConfig {

    @Bean @Primary
    public DelayQueue delayQueue() {
        return new InMemoryDelayQueue();
    }

    @Bean @Primary
    public TaskMessagePublisher taskMessagePublisher() {
        return new CapturingTaskMessagePublisher();
    }
}
