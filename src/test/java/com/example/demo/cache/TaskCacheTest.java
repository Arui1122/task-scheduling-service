package com.example.demo.cache;

import com.example.demo.controller.dto.TaskResponse;
import com.example.demo.delayqueue.DelayQueue;
import com.example.demo.delayqueue.InMemoryDelayQueue;
import com.example.demo.model.Task;
import com.example.demo.model.TaskStatus;
import com.example.demo.mq.CapturingTaskMessagePublisher;
import com.example.demo.mq.TaskMessagePublisher;
import com.example.demo.repository.TaskRepository;
import com.example.demo.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the Redis read-through cache behavior on the task read path,
 * using an in-memory ConcurrentMapCacheManager (the same Spring Cache
 * abstraction the Redis cache manager plugs into) so no broker is needed.
 *
 * The behavior under test is the @Cacheable / @CacheEvict wiring on
 * TaskService — that a second read is served from cache, and that the
 * write paths (cancel, trigger) evict so stale state is never served.
 */
@SpringBootTest(classes = TaskCacheTest.CacheTestConfig.class)
class TaskCacheTest {

    private static final Instant FUTURE = Instant.parse("2030-01-01T00:00:00Z");

    @Autowired
    TaskService service;

    @Autowired
    TaskRepository repo;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void clearState() {
        reset(repo);
        cacheManager.getCache("tasks").clear();
    }

    private Task mockTask(String id, TaskStatus status, long version) {
        Task t = mock(Task.class);
        when(t.getTaskId()).thenReturn(id);
        when(t.getExecuteAt()).thenReturn(FUTURE);
        when(t.getPayload()).thenReturn(Map.of("k", "v"));
        when(t.getStatus()).thenReturn(status);
        when(t.getVersion()).thenReturn(version);
        when(t.getCreatedAt()).thenReturn(Instant.parse("2026-06-25T00:00:00Z"));
        when(t.getTriggeredAt()).thenReturn(null);
        return t;
    }

    @Test
    void secondRead_isServedFromCache_repoQueriedOnce() {
        Task t1 = mockTask("t1", TaskStatus.PENDING, 0L);
        when(repo.findById("t1")).thenReturn(Optional.of(t1));

        TaskResponse first = service.getTaskResponse("t1");
        TaskResponse second = service.getTaskResponse("t1");

        assertThat(first.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(second).isEqualTo(first);
        verify(repo, times(1)).findById("t1");  // second call hit the cache
    }

    @Test
    void cancel_evictsCacheEntry() {
        Task t2 = mockTask("t2", TaskStatus.PENDING, 3L);
        when(repo.findById("t2")).thenReturn(Optional.of(t2));
        when(repo.cancelIfPending("t2", 3L)).thenReturn(1);

        service.getTaskResponse("t2");   // populate cache
        assertThat(cacheManager.getCache("tasks").get("t2")).isNotNull();

        service.cancel("t2");            // must evict

        assertThat(cacheManager.getCache("tasks").get("t2")).isNull();
    }

    @Test
    void trigger_evictsCache_nextReadSeesNewStatus() {
        Task pending = mockTask("t3", TaskStatus.PENDING, 0L);
        when(repo.findById("t3")).thenReturn(Optional.of(pending));
        when(repo.markTriggered(eq("t3"), anyLong(), any(Instant.class))).thenReturn(1);

        TaskResponse before = service.getTaskResponse("t3");   // cache PENDING
        assertThat(before.status()).isEqualTo(TaskStatus.PENDING);

        boolean triggered = service.tryTrigger("t3");          // must evict
        assertThat(triggered).isTrue();

        // repo now reports the task as TRIGGERED
        Task afterTrigger = mockTask("t3", TaskStatus.TRIGGERED, 1L);
        when(repo.findById("t3")).thenReturn(Optional.of(afterTrigger));
        TaskResponse after = service.getTaskResponse("t3");

        assertThat(after.status()).isEqualTo(TaskStatus.TRIGGERED);
    }

    @Test
    void missingTask_isNotCached() {
        when(repo.findById("ghost")).thenReturn(Optional.empty());

        try {
            service.getTaskResponse("ghost");
        } catch (RuntimeException expected) {
            // TaskNotFoundException -> 404; must not be cached
        }
        try {
            service.getTaskResponse("ghost");
        } catch (RuntimeException expected) {
            // ignored
        }

        verify(repo, times(2)).findById("ghost");  // not-found never cached
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("tasks");
        }

        @Bean
        TaskRepository taskRepository() {
            return mock(TaskRepository.class);
        }

        @Bean
        DelayQueue delayQueue() {
            return new InMemoryDelayQueue();
        }

        @Bean
        TaskMessagePublisher taskMessagePublisher() {
            return new CapturingTaskMessagePublisher();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        TaskService taskService(TaskRepository repo,
                                DelayQueue delayQueue,
                                TaskMessagePublisher publisher,
                                Clock clock) {
            return new TaskService(repo, delayQueue, publisher, clock, 5L);
        }
    }
}
