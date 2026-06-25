package com.example.demo.delayqueue;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDelayQueueTest {

    @Test
    void pollDue_returnsDueTasksSortedAsc() {
        InMemoryDelayQueue q = new InMemoryDelayQueue();
        q.add("a", Instant.parse("2030-01-01T00:00:02Z"));
        q.add("b", Instant.parse("2030-01-01T00:00:01Z"));
        q.add("c", Instant.parse("2030-01-01T00:00:05Z"));

        assertThat(q.pollDue(Instant.parse("2030-01-01T00:00:03Z"), 10))
                .containsExactly("b", "a");
    }

    @Test
    void pollDue_doesNotRemove() {
        InMemoryDelayQueue q = new InMemoryDelayQueue();
        q.add("a", Instant.parse("2030-01-01T00:00:01Z"));
        q.pollDue(Instant.parse("2030-01-01T00:00:02Z"), 10);
        assertThat(q.pollDue(Instant.parse("2030-01-01T00:00:02Z"), 10)).containsExactly("a");
    }

    @Test
    void remove_isIdempotent() {
        InMemoryDelayQueue q = new InMemoryDelayQueue();
        q.add("a", Instant.parse("2030-01-01T00:00:01Z"));
        q.remove("a");
        q.remove("a");
        assertThat(q.pollDue(Instant.parse("2030-01-01T00:00:02Z"), 10)).isEmpty();
    }

    @Test
    void add_replacesExistingScore() {
        InMemoryDelayQueue q = new InMemoryDelayQueue();
        q.add("a", Instant.parse("2030-01-01T00:00:10Z"));
        q.add("a", Instant.parse("2030-01-01T00:00:01Z"));
        assertThat(q.pollDue(Instant.parse("2030-01-01T00:00:02Z"), 10)).containsExactly("a");
    }
}
