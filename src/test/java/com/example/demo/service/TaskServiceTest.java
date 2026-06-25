package com.example.demo.service;

import com.example.demo.delayqueue.DelayQueue;
import com.example.demo.delayqueue.InMemoryDelayQueue;
import com.example.demo.model.Task;
import com.example.demo.model.TaskMessage;
import com.example.demo.model.TaskStatus;
import com.example.demo.mq.CapturingTaskMessagePublisher;
import com.example.demo.mq.MessagePublishException;
import com.example.demo.mq.TaskMessagePublisher;
import com.example.demo.repository.TaskRepository;
import com.example.demo.service.exception.IllegalTaskStateException;
import com.example.demo.service.exception.TaskAlreadyExistsException;
import com.example.demo.service.exception.TaskNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private TaskRepository repo;
    private DelayQueue delayQueue;
    private CapturingTaskMessagePublisher publisher;
    private TaskService service;

    @BeforeEach
    void setup() {
        repo = mock(TaskRepository.class);
        delayQueue = new InMemoryDelayQueue();
        publisher = new CapturingTaskMessagePublisher();
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new TaskService(repo, delayQueue, publisher, fixedClock);
    }

    private Task fakeStored(String id, Instant executeAt, TaskStatus status, long version) {
        Task t = mock(Task.class);
        when(t.getTaskId()).thenReturn(id);
        when(t.getExecuteAt()).thenReturn(executeAt);
        when(t.getStatus()).thenReturn(status);
        when(t.getVersion()).thenReturn(version);
        when(t.getPayload()).thenReturn(Map.of("k", "v"));
        return t;
    }

    @Test
    void create_happyPath_savesAndEnqueues() {
        Instant futureExec = NOW.plusSeconds(60);
        when(repo.existsById("abc")).thenReturn(false);
        when(repo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task t = service.create("abc", futureExec, Map.of("type", "email"));

        assertThat(t.getTaskId()).isEqualTo("abc");
        assertThat(delayQueue.pollDue(futureExec, 10)).containsExactly("abc");
        verify(repo).save(any(Task.class));
    }

    @Test
    void create_rejectsExecuteAtTooFarInPast() {
        Instant pastExec = NOW.minusSeconds(60);  // far past
        assertThatThrownBy(() -> service.create("abc", pastExec, Map.of("k", "v")))
                .isInstanceOf(IllegalTaskStateException.class)
                .hasMessageContaining("past");
        verify(repo, never()).save(any());
    }

    @Test
    void create_acceptsExecuteAtWithinGraceWindow() {
        Instant nearPast = NOW.minusSeconds(3);  // within 5s grace
        when(repo.existsById(anyString())).thenReturn(false);
        when(repo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create("abc", nearPast, Map.of("k", "v"));
        verify(repo).save(any(Task.class));
    }

    @Test
    void create_duplicate_throwsTaskAlreadyExists() {
        Instant futureExec = NOW.plusSeconds(60);
        when(repo.existsById("abc")).thenReturn(true);
        assertThatThrownBy(() -> service.create("abc", futureExec, Map.of("k", "v")))
                .isInstanceOf(TaskAlreadyExistsException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_dataIntegrityViolation_alsoMapsToAlreadyExists() {
        Instant futureExec = NOW.plusSeconds(60);
        when(repo.existsById("abc")).thenReturn(false);
        when(repo.save(any(Task.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> service.create("abc", futureExec, Map.of("k", "v")))
                .isInstanceOf(TaskAlreadyExistsException.class);
    }

    @Test
    void get_returnsTask() {
        Task t = fakeStored("abc", NOW.plusSeconds(10), TaskStatus.PENDING, 0L);
        when(repo.findById("abc")).thenReturn(Optional.of(t));
        assertThat(service.get("abc")).isSameAs(t);
    }

    @Test
    void get_throwsNotFound() {
        when(repo.findById("abc")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("abc")).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void list_delegatesToRepo() {
        Task t = fakeStored("abc", NOW.plusSeconds(10), TaskStatus.PENDING, 0L);
        Pageable page = PageRequest.of(0, 20);
        when(repo.findByStatus(TaskStatus.PENDING, page)).thenReturn(new PageImpl<>(List.of(t)));
        Page<Task> result = service.list(TaskStatus.PENDING, page);
        assertThat(result.getContent()).containsExactly(t);
    }

    @Test
    void cancel_happyPath_casSucceedsAndRemovesFromQueue() {
        Task t = fakeStored("abc", NOW.plusSeconds(10), TaskStatus.PENDING, 3L);
        when(repo.findById("abc")).thenReturn(Optional.of(t));
        when(repo.cancelIfPending("abc", 3L)).thenReturn(1);
        delayQueue.add("abc", NOW.plusSeconds(10));

        service.cancel("abc");

        verify(repo).cancelIfPending("abc", 3L);
        assertThat(delayQueue.pollDue(NOW.plusSeconds(20), 10)).isEmpty();
    }

    @Test
    void cancel_notFound_throws404() {
        when(repo.findById("abc")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel("abc"))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void cancel_alreadyTriggered_throwsConflict() {
        Task triggered = fakeStored("abc", NOW.minusSeconds(10), TaskStatus.TRIGGERED, 2L);
        when(repo.findById("abc")).thenReturn(Optional.of(triggered));
        when(repo.cancelIfPending("abc", 2L)).thenReturn(0);
        assertThatThrownBy(() -> service.cancel("abc"))
                .isInstanceOf(IllegalTaskStateException.class)
                .hasMessageContaining("triggered");
    }

    @Test
    void cancel_alreadyCancelled_isIdempotent() {
        Task cancelled = fakeStored("abc", NOW.minusSeconds(10), TaskStatus.CANCELLED, 2L);
        when(repo.findById("abc")).thenReturn(Optional.of(cancelled));
        when(repo.cancelIfPending("abc", 2L)).thenReturn(0);
        service.cancel("abc");  // no throw
    }

    @Test
    void tryTrigger_casSucceedsThenPublishesAndRemoves() {
        Task t = fakeStored("abc", NOW.minusSeconds(1), TaskStatus.PENDING, 5L);
        when(repo.findById("abc")).thenReturn(Optional.of(t));
        when(repo.markTriggered(eq("abc"), eq(5L), any(Instant.class))).thenReturn(1);
        delayQueue.add("abc", NOW.minusSeconds(1));

        boolean ok = service.tryTrigger("abc");

        assertThat(ok).isTrue();
        assertThat(publisher.getSent()).hasSize(1);
        assertThat(publisher.getSent().get(0).taskId()).isEqualTo("abc");
        assertThat(delayQueue.pollDue(NOW.plusSeconds(60), 10)).isEmpty();
    }

    @Test
    void tryTrigger_casFails_skipsPublishAndReturnsFalse() {
        Task t = fakeStored("abc", NOW.minusSeconds(1), TaskStatus.PENDING, 5L);
        when(repo.findById("abc")).thenReturn(Optional.of(t));
        when(repo.markTriggered(anyString(), anyLong(), any(Instant.class))).thenReturn(0);

        boolean ok = service.tryTrigger("abc");

        assertThat(ok).isFalse();
        assertThat(publisher.getSent()).isEmpty();
    }

    @Test
    void tryTrigger_mqFailure_revertsAndReturnsFalse() {
        Task t = fakeStored("abc", NOW.minusSeconds(1), TaskStatus.PENDING, 5L);
        when(repo.findById("abc")).thenReturn(Optional.of(t));
        when(repo.markTriggered(anyString(), anyLong(), any(Instant.class))).thenReturn(1);
        TaskMessagePublisher failing = msg -> { throw new MessagePublishException("down", null); };
        TaskService failingService = new TaskService(repo, delayQueue, failing,
                Clock.fixed(NOW, ZoneOffset.UTC));
        delayQueue.add("abc", NOW.minusSeconds(1));

        boolean ok = failingService.tryTrigger("abc");

        assertThat(ok).isFalse();
        verify(repo).revertToPending("abc");
        // queue NOT removed — next tick retries
        assertThat(delayQueue.pollDue(NOW.plusSeconds(1), 10)).containsExactly("abc");
    }

    @Test
    void tryTrigger_taskMissing_returnsFalseGracefully() {
        when(repo.findById("ghost")).thenReturn(Optional.empty());
        assertThat(service.tryTrigger("ghost")).isFalse();
    }
}
