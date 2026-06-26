-- Task scheduling service schema.
-- task_id is user-provided (per README example "abc-123"); the unique
-- primary key naturally enforces idempotency on duplicate POSTs.
-- idx_status_execute_at covers the backfill query:
--   WHERE status='PENDING' AND execute_at <= ?

CREATE TABLE IF NOT EXISTS tasks (
    task_id        VARCHAR(64)   NOT NULL,
    execute_at     DATETIME(3)   NOT NULL    COMMENT 'Scheduled execution time (UTC)',
    payload        JSON          NOT NULL    COMMENT 'Opaque JSON forwarded to MQ',
    status         VARCHAR(16)   NOT NULL    COMMENT 'PENDING | TRIGGERED | CANCELLED | FAILED',
    version        BIGINT        NOT NULL DEFAULT 0
                                              COMMENT 'Optimistic lock (@Version)',
    created_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                              ON UPDATE CURRENT_TIMESTAMP(3),
    triggered_at   DATETIME(3)   NULL        COMMENT 'When the task was actually published to MQ',
    PRIMARY KEY (task_id),
    KEY idx_status_execute_at (status, execute_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
