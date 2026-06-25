package com.example.demo.service;

import com.example.demo.delayqueue.DelayQueue;
import com.example.demo.domain.Task;
import com.example.demo.domain.TaskStatus;
import com.example.demo.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class TaskTriggerScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskTriggerScheduler.class);

    private final TaskService taskService;
    private final TaskRepository repo;
    private final DelayQueue delayQueue;
    private final Clock clock;
    private final int batchSize;
    private final long backfillGraceSeconds;

    public TaskTriggerScheduler(
            TaskService taskService,
            TaskRepository repo,
            DelayQueue delayQueue,
            Clock clock,
            @Value("${scheduler.batch-size}") int batchSize,
            @Value("${scheduler.backfill-grace-seconds}") long backfillGraceSeconds
    ) {
        this.taskService = taskService;
        this.repo = repo;
        this.delayQueue = delayQueue;
        this.clock = clock;
        this.batchSize = batchSize;
        this.backfillGraceSeconds = backfillGraceSeconds;
    }

    /**
     * Main path: pull due ids from Redis ZSet and try to trigger each.
     * Each id is processed independently — a failure on one does not stop
     * the rest of the batch.
     */
    @Scheduled(fixedDelayString = "${scheduler.main-poll-interval-ms}")
    public void pollDueTasks() {
        List<String> ids = delayQueue.pollDue(clock.instant(), batchSize);
        for (String id : ids) {
            try {
                taskService.tryTrigger(id);
            } catch (Exception e) {
                log.warn("tryTrigger failed for {}; will retry next tick", id, e);
            }
        }
    }

    /**
     * Backfill: tasks PENDING in MySQL but missing from Redis (e.g., the
     * ZADD failed, Redis lost data, app crashed before enqueue) get
     * re-enqueued. The main path then publishes them on the next tick.
     */
    @Scheduled(fixedDelayString = "${scheduler.backfill-interval-ms}")
    public void backfillFromDb() {
        Instant cutoff = clock.instant().minusSeconds(backfillGraceSeconds);
        List<Task> stale = repo.findDueForBackfill(cutoff, TaskStatus.PENDING,
                PageRequest.of(0, batchSize));
        for (Task t : stale) {
            try {
                delayQueue.add(t.getTaskId(), t.getExecuteAt());
            } catch (Exception e) {
                log.warn("Backfill re-enqueue failed for {}", t.getTaskId(), e);
            }
        }
    }
}
