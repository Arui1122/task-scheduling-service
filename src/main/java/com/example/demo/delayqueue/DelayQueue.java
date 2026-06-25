package com.example.demo.delayqueue;

import java.time.Instant;
import java.util.List;

/**
 * Time-ordered queue of task ids. Stored externally so the scheduler can
 * pull ids whose due time has arrived without scanning the database every
 * tick. Removal is explicit and only happens after MQ publish succeeds.
 */
public interface DelayQueue {

    /** Add or update a task with a due time. */
    void add(String taskId, Instant executeAt);

    /**
     * Return up to `limit` task ids whose executeAt &lt;= `now`, sorted
     * by executeAt ascending. Does NOT remove them — callers remove via
     * {@link #remove(String)} only after successfully handling each id.
     */
    List<String> pollDue(Instant now, int limit);

    /** Remove a single task id (idempotent). */
    void remove(String taskId);
}
