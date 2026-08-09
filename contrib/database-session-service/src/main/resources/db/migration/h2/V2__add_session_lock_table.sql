-- H2 V2: add adk_session_lock table for DatabaseLockManager

CREATE TABLE adk_session_lock (
    lock_key    VARCHAR(256) NOT NULL,
    owner_id    VARCHAR(128) NOT NULL,
    expire_at   BIGINT       NOT NULL,
    PRIMARY KEY (lock_key)
);
