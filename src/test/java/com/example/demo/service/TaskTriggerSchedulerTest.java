package com.example.demo.service;

import com.example.demo.delayqueue.DelayQueue;
import com.example.demo.delayqueue.InMemoryDelayQueue;
import com.example.demo.model.Task;
import com.example.demo.model.TaskStatus;
import com.example.demo.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskTriggerSchedulerTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private TaskRepository repo;
    private TaskService taskService;
    private DelayQueue queue;
    private TaskTriggerScheduler scheduler;

    @BeforeEach
    void setup() {
        repo = mock(TaskRepository.class);
        taskService = mock(TaskService.class);
        queue = new InMemoryDelayQueue();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        scheduler = new TaskTriggerScheduler(taskService, repo, queue, clock, 100, 30);
    }

    private Task pendingTask(String id, Instant executeAt) {
        Task t = mock(Task.class);
        when(t.getTaskId()).thenReturn(id);
        when(t.getExecuteAt()).thenReturn(executeAt);
        when(t.getStatus()).thenReturn(TaskStatus.PENDING);
        when(t.getVersion()).thenReturn(0L);
        when(t.getPayload()).thenReturn(Map.of("k", "v"));
        return t;
    }

    @Test
    void pollDueTasks_callsTryTriggerForEachDueId() {
        queue.add("a", NOW.minusSeconds(1));
        queue.add("b", NOW.minusSeconds(2));

        scheduler.pollDueTasks();

        verify(taskService).tryTrigger("a");
        verify(taskService).tryTrigger("b");
    }

    @Test
    void pollDueTasks_doesNothingForEmptyQueue() {
        scheduler.pollDueTasks();
        verify(taskService, never()).tryTrigger(anyString());
    }

    @Test
    void pollDueTasks_skipsFutureTasks() {
        queue.add("future", NOW.plusSeconds(60));
        scheduler.pollDueTasks();
        verify(taskService, never()).tryTrigger(anyString());
    }

    @Test
    void pollDueTasks_continuesIfOneFails() {
        queue.add("a", NOW.minusSeconds(2));
        queue.add("b", NOW.minusSeconds(1));
        when(taskService.tryTrigger("a")).thenThrow(new RuntimeException("boom"));

        scheduler.pollDueTasks();

        verify(taskService).tryTrigger("a");
        verify(taskService).tryTrigger("b");
    }

    @Test
    void backfillFromDb_reAddsStalePendingToQueue() {
        Task t1 = pendingTask("x", NOW.minusSeconds(120));
        Task t2 = pendingTask("y", NOW.minusSeconds(90));
        when(repo.findDueForBackfill(eq(NOW.minusSeconds(30)), eq(TaskStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(t1, t2));

        scheduler.backfillFromDb();

        assertThat(queue.pollDue(NOW.plusSeconds(60), 100)).containsExactlyInAnyOrder("x", "y");
        verify(taskService, never()).tryTrigger(anyString());  // backfill does NOT publish directly
    }

    @Test
    void backfillFromDb_doesNothingWhenNoneStale() {
        when(repo.findDueForBackfill(any(), eq(TaskStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());
        scheduler.backfillFromDb();
        assertThat(queue.pollDue(NOW.plusSeconds(60), 100)).isEmpty();
    }
}
