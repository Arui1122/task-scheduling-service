package com.example.demo.delayqueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDelayQueueTest {

    private StringRedisTemplate redis;
    @SuppressWarnings("unchecked")
    private ZSetOperations<String, String> zset;
    private RedisDelayQueue queue;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        queue = new RedisDelayQueue(redis);
    }

    @Test
    void add_callsZSetAddWithEpochMillisScore() {
        queue.add("abc", Instant.parse("2030-01-01T00:00:00Z"));
        verify(zset).add(eq("task:delay"), eq("abc"), eq(1893456000000d));
    }

    @Test
    void pollDue_callsRangeByScoreAndReturnsIds() {
        TypedTuple<String> t1 = mock(TypedTuple.class);
        when(t1.getValue()).thenReturn("a");
        TypedTuple<String> t2 = mock(TypedTuple.class);
        when(t2.getValue()).thenReturn("b");
        Set<TypedTuple<String>> result = new LinkedHashSet<>(List.of(t1, t2));
        when(zset.rangeByScoreWithScores(eq("task:delay"), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(result);

        List<String> ids = queue.pollDue(Instant.parse("2030-01-01T00:00:00Z"), 10);

        assertThat(ids).containsExactly("a", "b");
    }

    @Test
    void pollDue_emptyOrNullSetReturnsEmptyList() {
        when(zset.rangeByScoreWithScores(any(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(null);
        assertThat(queue.pollDue(Instant.parse("2030-01-01T00:00:00Z"), 10)).isEmpty();
    }

    @Test
    void remove_callsZSetRemove() {
        queue.remove("abc");
        verify(zset).remove("task:delay", new Object[] { "abc" });
    }
}
