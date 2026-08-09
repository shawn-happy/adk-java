-- Oracle initial schema for ADK DatabaseSessionService (v1, mirrors Python v1)

CREATE TABLE adk_sessions (
    app_name    VARCHAR2(128) NOT NULL,
    user_id     VARCHAR2(128) NOT NULL,
    id          VARCHAR2(128) NOT NULL,
    state       CLOB         ,
    create_time NUMBER(19)    NOT NULL,
    update_time NUMBER(19)    NOT NULL,
    CONSTRAINT pk_adk_sessions PRIMARY KEY (app_name, user_id, id)
);

CREATE TABLE adk_events (
    id            VARCHAR2(128) NOT NULL,
    app_name      VARCHAR2(128) NOT NULL,
    user_id       VARCHAR2(128) NOT NULL,
    session_id    VARCHAR2(128) NOT NULL,
    invocation_id VARCHAR2(256),
    timestamp     NUMBER(19)    NOT NULL,
    event_data    CLOB,
    CONSTRAINT pk_adk_events PRIMARY KEY (id, app_name, user_id, session_id),
    CONSTRAINT fk_adk_events_session
        FOREIGN KEY (app_name, user_id, session_id)
        REFERENCES adk_sessions(app_name, user_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_events_app_user_session_ts
    ON adk_events (app_name, user_id, session_id, timestamp DESC);

CREATE TABLE adk_app_states (
    app_name    VARCHAR2(128) NOT NULL,
    state       CLOB         ,
    update_time NUMBER(19)    NOT NULL,
    CONSTRAINT pk_adk_app_states PRIMARY KEY (app_name)
);

CREATE TABLE adk_user_states (
    app_name    VARCHAR2(128) NOT NULL,
    user_id     VARCHAR2(128) NOT NULL,
    state       CLOB         ,
    update_time NUMBER(19)    NOT NULL,
    CONSTRAINT pk_adk_user_states PRIMARY KEY (app_name, user_id)
);

CREATE TABLE adk_internal_metadata (
    key   VARCHAR2(128) NOT NULL,
    value VARCHAR2(256),
    CONSTRAINT pk_adk_internal_metadata PRIMARY KEY (key)
);

INSERT INTO adk_internal_metadata (key, value) VALUES ('schema_version', '1');
