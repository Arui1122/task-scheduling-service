package com.example.demo.delayqueue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe in-memory fake used by service tests and the integration
 * test. Not for production use; sorted by (executeAt, taskId) only.
 */
public class InMemoryDelayQueue implements DelayQueue {

    private final NavigableMap<Long, List<String>> byTime = new ConcurrentSkipListMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Long> reverse =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void add(String taskId, Instant executeAt) {
        long score = executeAt.toEpochMilli();
        lock.lock();
        try {
            Long previous = reverse.put(taskId, score);
            if (previous != null) {
                List<String> bucket = byTime.get(previous);
                if (bucket != null) bucket.remove(taskId);
            }
            byTime.computeIfAbsent(score, k -> new CopyOnWriteArrayList<>()).add(taskId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<String> pollDue(Instant now, int limit) {
        List<String> result = new ArrayList<>(limit);
        lock.lock();
        try {
            for (Map.Entry<Long, List<String>> e : byTime.headMap(now.toEpochMilli(), true).entrySet()) {
                for (String id : e.getValue()) {
                    if (result.size() >= limit) return result;
                    result.add(id);
                }
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public void remove(String taskId) {
        lock.lock();
        try {
            Long score = reverse.remove(taskId);
            if (score == null) return;
            List<String> bucket = byTime.get(score);
            if (bucket != null) {
                bucket.remove(taskId);
                if (bucket.isEmpty()) byTime.remove(score);
            }
        } finally {
            lock.unlock();
        }
    }
}
