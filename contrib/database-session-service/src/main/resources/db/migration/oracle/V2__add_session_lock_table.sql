-- Oracle V2: add adk_session_lock table for DatabaseLockManager

CREATE TABLE adk_session_lock (
    lock_key    VARCHAR2(256) NOT NULL,
    owner_id    VARCHAR2(128) NOT NULL,
    expire_at   NUMBER(19)    NOT NULL,
    CONSTRAINT pk_adk_session_lock PRIMARY KEY (lock_key)
);
