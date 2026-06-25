package com.example.demo.repository;

import com.example.demo.config.ClockConfig;
import com.example.demo.config.JpaAuditingConfig;
import com.example.demo.model.Task;
import com.example.demo.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
// @DataJpaTest excludes regular @Configuration classes; import auditing setup explicitly.
@Import({ClockConfig.class, JpaAuditingConfig.class})
class TaskRepositoryTest {

    @Autowired
    TaskRepository repo;

    private Task pending(String id, Instant executeAt) {
        Task t = new Task(id, executeAt, Map.of("k", "v"), TaskStatus.PENDING);
        return repo.saveAndFlush(t);
    }

    @Test
    void markTriggered_succeeds_whenVersionAndStatusMatch() {
        Task t = pending("t1", Instant.parse("2030-01-01T00:00:00Z"));
        int rows = repo.markTriggered(t.getTaskId(), t.getVersion(), Instant.parse("2030-01-01T00:00:01Z"));
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void markTriggered_fails_whenVersionStale() {
        Task t = pending("t2", Instant.parse("2030-01-01T00:00:00Z"));
        int rows = repo.markTriggered(t.getTaskId(), t.getVersion() + 1, Instant.parse("2030-01-01T00:00:01Z"));
        assertThat(rows).isZero();
    }

    @Test
    void markTriggered_fails_whenAlreadyCancelled() {
        Task t = pending("t3", Instant.parse("2030-01-01T00:00:00Z"));
        repo.cancelIfPending(t.getTaskId(), t.getVersion());
        int rows = repo.markTriggered(t.getTaskId(), t.getVersion() + 1, Instant.parse("2030-01-01T00:00:01Z"));
        assertThat(rows).isZero();
    }

    @Test
    void revertToPending_movesTriggeredBackToPending() {
        Task t = pending("t4", Instant.parse("2030-01-01T00:00:00Z"));
        repo.markTriggered(t.getTaskId(), t.getVersion(), Instant.parse("2030-01-01T00:00:01Z"));
        int rows = repo.revertToPending(t.getTaskId());
        assertThat(rows).isEqualTo(1);
        Task reloaded = repo.findById("t4").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void cancelIfPending_succeedsOncePerTask() {
        Task t = pending("t5", Instant.parse("2030-01-01T00:00:00Z"));
        int first = repo.cancelIfPending(t.getTaskId(), t.getVersion());
        int second = repo.cancelIfPending(t.getTaskId(), t.getVersion());
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void findDueForBackfill_returnsOnlyPendingBeforeCutoffSortedAsc() {
        pending("a", Instant.parse("2030-01-01T00:00:00Z"));
        pending("b", Instant.parse("2029-12-31T23:59:00Z"));
        pending("c", Instant.parse("2031-01-01T00:00:00Z"));  // future, should not appear
        Task d = pending("d", Instant.parse("2029-12-31T00:00:00Z"));
        repo.cancelIfPending(d.getTaskId(), d.getVersion());  // cancelled, should not appear

        List<Task> due = repo.findDueForBackfill(
                Instant.parse("2030-01-01T00:00:00Z"),
                TaskStatus.PENDING,
                PageRequest.of(0, 10));

        assertThat(due).extracting(Task::getTaskId).containsExactly("b", "a");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentMarkTriggered_onlyOneSucceeds() throws Exception {
        Task t = pending("race", Instant.parse("2030-01-01T00:00:00Z"));
        long version = t.getVersion();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try { fire.await(); } catch (InterruptedException ignored) {}
                int rows = repo.markTriggered("race", version, Instant.parse("2030-01-01T00:00:01Z"));
                if (rows == 1) wins.incrementAndGet();
            });
        }
        ready.await();
        fire.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        assertThat(wins.get()).isEqualTo(1);
    }
}
