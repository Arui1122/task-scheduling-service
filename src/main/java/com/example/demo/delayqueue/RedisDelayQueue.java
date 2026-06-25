package com.example.demo.delayqueue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ZSet-backed DelayQueue. score = executeAt epoch millis; member = taskId.
 * Polls with ZRANGEBYSCORE (O(log N + M)). Removal is explicit so that
 * an entry stays in the ZSet if MQ publish fails — the next tick will
 * retry it.
 */
@Component
public class RedisDelayQueue implements DelayQueue {

    private static final String KEY = "task:delay";

    private final StringRedisTemplate redis;

    public RedisDelayQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void add(String taskId, Instant executeAt) {
        redis.opsForZSet().add(KEY, taskId, executeAt.toEpochMilli());
    }

    @Override
    public List<String> pollDue(Instant now, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().rangeByScoreWithScores(KEY, 0, now.toEpochMilli(), 0, limit);
        if (tuples == null || tuples.isEmpty()) return List.of();
        List<String> ids = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            String v = t.getValue();
            if (v != null) ids.add(v);
        }
        return ids;
    }

    @Override
    public void remove(String taskId) {
        redis.opsForZSet().remove(KEY, taskId);
    }
}
