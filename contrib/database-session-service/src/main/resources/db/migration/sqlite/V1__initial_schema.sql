-- SQLite initial schema for ADK DatabaseSessionService (v1, mirrors Python v1)

CREATE TABLE adk_sessions (
    app_name    TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    id          TEXT NOT NULL,
    state       TEXT NOT NULL,
    create_time INTEGER NOT NULL,
    update_time INTEGER NOT NULL,
    PRIMARY KEY (app_name, user_id, id)
);

CREATE TABLE adk_events (
    id            TEXT NOT NULL,
    app_name      TEXT NOT NULL,
    user_id       TEXT NOT NULL,
    session_id    TEXT NOT NULL,
    invocation_id TEXT,
    timestamp     INTEGER NOT NULL,
    event_data    TEXT,
    PRIMARY KEY (id, app_name, user_id, session_id),
    FOREIGN KEY (app_name, user_id, session_id)
        REFERENCES adk_sessions(app_name, user_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_events_app_user_session_ts
    ON adk_events (app_name, user_id, session_id, timestamp DESC);

CREATE TABLE adk_app_states (
    app_name    TEXT NOT NULL,
    state       TEXT NOT NULL,
    update_time INTEGER NOT NULL,
    PRIMARY KEY (app_name)
);

CREATE TABLE adk_user_states (
    app_name    TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    state       TEXT NOT NULL,
    update_time INTEGER NOT NULL,
    PRIMARY KEY (app_name, user_id)
);

CREATE TABLE adk_internal_metadata (
    key   TEXT NOT NULL,
    value TEXT,
    PRIMARY KEY (key)
);

INSERT INTO adk_internal_metadata (key, value) VALUES ('schema_version', '1');
