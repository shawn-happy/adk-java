-- H2 initial schema for ADK DatabaseSessionService (v1, mirrors Python v1)

CREATE TABLE adk_sessions (
    app_name    VARCHAR(128) NOT NULL,
    user_id     VARCHAR(128) NOT NULL,
    id          VARCHAR(128) NOT NULL,
    state       CLOB         NOT NULL,
    create_time BIGINT       NOT NULL,
    update_time BIGINT       NOT NULL,
    PRIMARY KEY (app_name, user_id, id)
);

CREATE TABLE adk_events (
    id            VARCHAR(128) NOT NULL,
    app_name      VARCHAR(128) NOT NULL,
    user_id       VARCHAR(128) NOT NULL,
    session_id    VARCHAR(128) NOT NULL,
    invocation_id VARCHAR(256),
    timestamp     BIGINT       NOT NULL,
    event_data    CLOB,
    PRIMARY KEY (id, app_name, user_id, session_id),
    FOREIGN KEY (app_name, user_id, session_id)
        REFERENCES adk_sessions(app_name, user_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_events_app_user_session_ts
    ON adk_events (app_name, user_id, session_id, timestamp DESC);

CREATE TABLE adk_app_states (
    app_name    VARCHAR(128) NOT NULL,
    state       CLOB         NOT NULL,
    update_time BIGINT       NOT NULL,
    PRIMARY KEY (app_name)
);

CREATE TABLE adk_user_states (
    app_name    VARCHAR(128) NOT NULL,
    user_id     VARCHAR(128) NOT NULL,
    state       CLOB         NOT NULL,
    update_time BIGINT       NOT NULL,
    PRIMARY KEY (app_name, user_id)
);

-- KEY and VALUE are reserved keywords in H2 2.x, so the column names must be quoted.
-- Quoting upper-case keeps unquoted references (which H2 folds to upper case) working.
CREATE TABLE adk_internal_metadata (
    "KEY"   VARCHAR(128) NOT NULL,
    "VALUE" VARCHAR(256),
    PRIMARY KEY ("KEY")
);

INSERT INTO adk_internal_metadata ("KEY", "VALUE") VALUES ('schema_version', '1');
