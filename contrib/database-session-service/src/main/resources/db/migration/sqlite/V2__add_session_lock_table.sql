-- SQLite V2: add adk_session_lock table for DatabaseLockManager

CREATE TABLE adk_session_lock (
    lock_key    TEXT    NOT NULL,
    owner_id    TEXT    NOT NULL,
    expire_at   INTEGER NOT NULL,
    PRIMARY KEY (lock_key)
);
