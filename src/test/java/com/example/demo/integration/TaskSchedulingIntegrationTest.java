package com.example.demo.integration;

import com.example.demo.domain.TaskMessage;
import com.example.demo.domain.TaskStatus;
import com.example.demo.mq.CapturingTaskMessagePublisher;
import com.example.demo.mq.TaskMessagePublisher;
import com.example.demo.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
class TaskSchedulingIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TaskRepository repo;
    @Autowired TaskMessagePublisher publisher;

    @Test
    void postTask_thenSchedulerTriggersAndPublishes() throws Exception {
        String taskId = "integ-" + System.nanoTime();
        Instant executeAt = Instant.now().plusSeconds(1);

        String body = mapper.writeValueAsString(Map.of(
                "taskId", taskId,
                "executeAt", executeAt.toString(),
                "payload", Map.of("type", "email", "target", "x@y.com")
        ));

        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        CapturingTaskMessagePublisher capturing = (CapturingTaskMessagePublisher) publisher;
        await().atMost(5, TimeUnit.SECONDS)
               .pollInterval(50, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   assertThat(capturing.getSent())
                           .extracting(TaskMessage::taskId)
                           .contains(taskId);
                   assertThat(repo.findById(taskId).orElseThrow().getStatus())
                           .isEqualTo(TaskStatus.TRIGGERED);
                   assertThat(repo.findById(taskId).orElseThrow().getTriggeredAt())
                           .isNotNull();
               });
    }
}
