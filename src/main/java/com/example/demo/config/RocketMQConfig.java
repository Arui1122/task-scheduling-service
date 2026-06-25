package com.example.demo.config;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")  // tests substitute a fake TaskMessagePublisher; the real producer is never created
public class RocketMQConfig {

    /**
     * Legacy TCP producer — works with the mqnamesrv:9876 + mqbroker:10911
     * topology in the provided docker-compose. The 5.x gRPC API would
     * require an additional mqproxy container.
     */
    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer rocketMQProducer(
            @Value("${rocketmq.name-server}") String nameServer,
            @Value("${rocketmq.producer-group}") String producerGroup,
            @Value("${rocketmq.send-timeout-ms:3000}") int sendTimeoutMs
    ) throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(sendTimeoutMs);
        producer.start();
        return producer;
    }
}
