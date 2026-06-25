package com.example.demo.repository;

import com.example.demo.domain.Task;
import com.example.demo.domain.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, String> {

    /**
     * CAS: only succeed if the task is still PENDING with the expected
     * version. Hibernate updates the version on success automatically
     * via @Version, but since we use a bulk @Modifying query we set it
     * explicitly to keep semantics identical. Runs in its own short
     * transaction so the scheduler can publish to MQ outside of it.
     */
    @Modifying
    @Transactional
    @Query("""
           update Task t
              set t.status = com.example.demo.domain.TaskStatus.TRIGGERED,
                  t.triggeredAt = :triggeredAt,
                  t.version = t.version + 1
            where t.taskId = :taskId
              and t.version = :expectedVersion
              and t.status = com.example.demo.domain.TaskStatus.PENDING
           """)
    int markTriggered(@Param("taskId") String taskId,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("triggeredAt") Instant triggeredAt);

    /**
     * Compensation for an MQ-send failure after a successful CAS to
     * TRIGGERED. Only flips back if the row is still in the TRIGGERED
     * state we set (defensive — protects against double-revert races).
     */
    @Modifying
    @Transactional
    @Query("""
           update Task t
              set t.status = com.example.demo.domain.TaskStatus.PENDING,
                  t.triggeredAt = null,
                  t.version = t.version + 1
            where t.taskId = :taskId
              and t.status = com.example.demo.domain.TaskStatus.TRIGGERED
           """)
    int revertToPending(@Param("taskId") String taskId);

    /**
     * CAS-cancel. Loses to a concurrent trigger (whichever UPDATE the DB
     * applies first wins). Idempotent: a second call sees status != PENDING
     * and returns 0.
     */
    @Modifying
    @Transactional
    @Query("""
           update Task t
              set t.status = com.example.demo.domain.TaskStatus.CANCELLED,
                  t.version = t.version + 1
            where t.taskId = :taskId
              and t.version = :expectedVersion
              and t.status = com.example.demo.domain.TaskStatus.PENDING
           """)
    int cancelIfPending(@Param("taskId") String taskId,
                        @Param("expectedVersion") Long expectedVersion);

    /**
     * Backfill query: PENDING tasks whose execute_at is older than the
     * cutoff. Hits the idx_status_execute_at covering index in MySQL.
     */
    @Query("""
           select t from Task t
            where t.status = :status
              and t.executeAt <= :cutoff
            order by t.executeAt asc
           """)
    List<Task> findDueForBackfill(@Param("cutoff") Instant cutoff,
                                  @Param("status") TaskStatus status,
                                  Pageable pageable);
}
