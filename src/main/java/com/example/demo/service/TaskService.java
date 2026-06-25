package com.example.demo.service;

import com.example.demo.delayqueue.DelayQueue;
import com.example.demo.model.Task;
import com.example.demo.model.TaskMessage;
import com.example.demo.model.TaskStatus;
import com.example.demo.mq.MessagePublishException;
import com.example.demo.mq.TaskMessagePublisher;
import com.example.demo.repository.TaskRepository;
import com.example.demo.service.exception.IllegalTaskStateException;
import com.example.demo.service.exception.TaskAlreadyExistsException;
import com.example.demo.service.exception.TaskNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final Duration CREATE_GRACE = Duration.ofSeconds(5);

    private final TaskRepository repo;
    private final DelayQueue delayQueue;
    private final TaskMessagePublisher publisher;
    private final Clock clock;

    public TaskService(TaskRepository repo,
                       DelayQueue delayQueue,
                       TaskMessagePublisher publisher,
                       Clock clock) {
        this.repo = repo;
        this.delayQueue = delayQueue;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public Task create(String taskId, Instant executeAt, Map<String, Object> payload) {
        Instant now = clock.instant();
        if (executeAt.isBefore(now.minus(CREATE_GRACE))) {
            throw new IllegalTaskStateException("executeAt is in the past beyond the grace window");
        }
        if (repo.existsById(taskId)) {
            throw new TaskAlreadyExistsException(taskId);
        }

        Task saved;
        try {
            saved = repo.save(new Task(taskId, executeAt, payload, TaskStatus.PENDING));
        } catch (DataIntegrityViolationException e) {
            // race: another caller inserted between existsById and save
            throw new TaskAlreadyExistsException(taskId);
        }

        // Best-effort enqueue; backfill will pick it up if Redis is down.
        try {
            delayQueue.add(taskId, executeAt);
        } catch (Exception e) {
            log.warn("DelayQueue add failed for task {}; backfill will retry", taskId, e);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Task get(String taskId) {
        return repo.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    @Transactional(readOnly = true)
    public Page<Task> list(TaskStatus status, Pageable pageable) {
        return repo.findByStatus(status, pageable);
    }

    @Transactional
    public void cancel(String taskId) {
        Task task = repo.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        int rows = repo.cancelIfPending(taskId, task.getVersion());
        if (rows == 1) {
            try { delayQueue.remove(taskId); } catch (Exception e) {
                log.warn("DelayQueue remove failed for cancelled task {}", taskId, e);
            }
            return;
        }
        // rows == 0 — re-read to decide whether it's a 409 or an idempotent 204.
        TaskStatus current = repo.findById(taskId).map(Task::getStatus).orElse(null);
        if (current == TaskStatus.CANCELLED) return;  // idempotent
        if (current == TaskStatus.TRIGGERED) {
            throw new IllegalTaskStateException("task already triggered");
        }
        throw new IllegalTaskStateException("task in unexpected state: " + current);
    }

    /**
     * Used by the scheduler. Returns true iff the CAS succeeded and the
     * MQ publish completed; on failure the DB state is reverted and the
     * caller leaves the entry in the delay queue for the next tick.
     */
    public boolean tryTrigger(String taskId) {
        Optional<Task> maybe = repo.findById(taskId);
        if (maybe.isEmpty()) {
            // Task disappeared between ZSet pull and DB lookup — stale entry.
            try { delayQueue.remove(taskId); } catch (Exception ignored) {}
            return false;
        }
        Task task = maybe.get();
        if (task.getStatus() != TaskStatus.PENDING) {
            try { delayQueue.remove(taskId); } catch (Exception ignored) {}
            return false;
        }

        // Phase 1: short tx CAS
        Instant now = clock.instant();
        int rows = repo.markTriggered(taskId, task.getVersion(), now);
        if (rows == 0) return false;  // lost the race

        // Phase 2: publish outside the transaction
        try {
            publisher.publish(new TaskMessage(taskId, task.getExecuteAt(), now, task.getPayload()));
            try { delayQueue.remove(taskId); } catch (Exception e) {
                log.warn("DelayQueue remove failed after successful publish for {}", taskId, e);
            }
            return true;
        } catch (Exception e) {
            log.warn("MQ publish failed for {}, reverting to PENDING for retry", taskId, e);
            try { repo.revertToPending(taskId); } catch (Exception revertEx) {
                log.error("Revert failed for {} — invariant broken", taskId, revertEx);
            }
            return false;
        }
    }
}
