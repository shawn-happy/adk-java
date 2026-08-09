-- PostgreSQL initial schema for ADK DatabaseSessionService (v1, mirrors Python v1)

CREATE TABLE adk_sessions (
    app_name    VARCHAR(128) NOT NULL,
    user_id     VARCHAR(128) NOT NULL,
    id          VARCHAR(128) NOT NULL,
    state       JSONB        NOT NULL::jsonb,
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
    event_data    JSONB,
    PRIMARY KEY (id, app_name, user_id, session_id),
    FOREIGN KEY (app_name, user_id, session_id)
        REFERENCES adk_sessions(app_name, user_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_events_app_user_session_ts
    ON adk_events (app_name, user_id, session_id, timestamp DESC);

CREATE TABLE adk_app_states (
    app_name    VARCHAR(128) NOT NULL,
    state       JSONB        NOT NULL::jsonb,
    update_time BIGINT       NOT NULL,
    PRIMARY KEY (app_name)
);

CREATE TABLE adk_user_states (
    app_name    VARCHAR(128) NOT NULL,
    user_id     VARCHAR(128) NOT NULL,
    state       JSONB        NOT NULL::jsonb,
    update_time BIGINT       NOT NULL,
    PRIMARY KEY (app_name, user_id)
);

CREATE TABLE adk_internal_metadata (
    key   VARCHAR(128) NOT NULL,
    value VARCHAR(256),
    PRIMARY KEY (key)
);

INSERT INTO adk_internal_metadata (key, value) VALUES ('schema_version', '1');
