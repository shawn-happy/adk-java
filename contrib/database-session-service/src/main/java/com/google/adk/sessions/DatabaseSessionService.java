/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.adk.sessions;

import com.google.adk.config.DataSourceConfig;
import com.google.adk.config.DatabaseConfig;
import com.google.adk.config.LockConfig;
import com.google.adk.dao.AppStateDao;
import com.google.adk.dao.EventDao;
import com.google.adk.dao.SessionDao;
import com.google.adk.dao.UserStateDao;
import com.google.adk.entity.AdkSession;
import com.google.adk.entity.StateDelta;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.lock.LockManager;
import com.google.adk.lock.LockManager.AdkSessionLock;
import com.google.adk.schema.DatabaseDialect;
import com.google.adk.schema.DialectDetector;
import com.google.adk.schema.SqlDialect;
import com.google.adk.util.JsonUtils;
import com.google.adk.util.StateDeltaUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Database-backed {@link BaseSessionService} supporting MySQL, PostgreSQL, Oracle, SQLite, and H2.
 *
 * <p>State is split across three tables following ADK's prefix convention: {@code app:} -> {@code
 * adk_app_states}, {@code user:} -> {@code adk_user_states}, unprefixed -> {@code
 * adk_sessions.state}. {@code temp:} keys are applied to the in-memory session only and never
 * persisted.
 *
 * <p>Concurrency is governed by a pluggable {@link LockManager} (Redis / InProcess / Database). The
 * database lock manager uses a dedicated {@code adk_session_lock} table with a unique {@code
 * lock_key} column, providing cross-database distributed locking without {@code FOR UPDATE}.
 *
 * <p>All JDBC access is delegated to DAOs ({@link SessionDao}, {@link EventDao}, {@link
 * AppStateDao}, {@link UserStateDao}) which use dialect-specific SQL provided by {@link
 * SqlDialect}. This class is responsible only for business logic, locking, and transaction
 * orchestration. Schema initialization is not performed automatically; callers must ensure the
 * {@code adk_*} tables exist before constructing this service (see the SQL scripts under {@code
 * src/main/resources/db/migration/}).
 */
public final class DatabaseSessionService implements BaseSessionService {

  private final TransactionTemplate transactionTemplate;
  private final LockManager lockManager;
  private final SqlDialect sqlDialect;
  private final SessionDao sessionDao;
  private final EventDao eventDao;
  private final AppStateDao appStateDao;
  private final UserStateDao userStateDao;

  /** Minimal constructor: auto DataSource, InProcessLockManager, auto dialect. */
  public DatabaseSessionService(String dbUrl, String username, String password) {
    this(
        DatabaseConfig.builder()
            .datasource(DataSourceConfig.of(dbUrl, username, password))
            .lock(LockConfig.local())
            .dialect("auto")
            .build());
  }

  /** Full constructor via {@link DatabaseConfig}. */
  public DatabaseSessionService(DatabaseConfig config) {
    DataSource dataSource = config.createDataSource();
    DatabaseDialect dialect = config.resolveDialect(dataSource);
    NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    this.transactionTemplate =
        new TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
    this.lockManager = config.createLockManager(dataSource);
    this.sqlDialect = DialectDetector.create(dialect);
    this.sessionDao = new SessionDao(jdbcTemplate, sqlDialect);
    this.eventDao = new EventDao(jdbcTemplate, sqlDialect);
    this.appStateDao = new AppStateDao(jdbcTemplate, sqlDialect);
    this.userStateDao = new UserStateDao(jdbcTemplate, sqlDialect);
  }

  /** DI-style constructor with an externally-managed {@link DataSource} and {@link LockManager}. */
  public DatabaseSessionService(DataSource dataSource, LockManager lockManager) {
    DatabaseDialect dialect = DialectDetector.detect(dataSource);
    NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    this.transactionTemplate =
        new TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
    this.lockManager = lockManager;
    this.sqlDialect = DialectDetector.create(dialect);
    this.sessionDao = new SessionDao(jdbcTemplate, sqlDialect);
    this.eventDao = new EventDao(jdbcTemplate, sqlDialect);
    this.appStateDao = new AppStateDao(jdbcTemplate, sqlDialect);
    this.userStateDao = new UserStateDao(jdbcTemplate, sqlDialect);
  }

  // ----------------------------------------------------------------------------------------------
  // createSession
  // ----------------------------------------------------------------------------------------------

  @Override
  @Deprecated
  public Single<Session> createSession(
      String appName,
      String userId,
      @Nullable ConcurrentMap<String, Object> state,
      @Nullable String sessionId) {
    return createSession(appName, userId, (Map<String, Object>) state, sessionId);
  }

  @Override
  public Single<Session> createSession(
      String appName,
      String userId,
      @Nullable Map<String, Object> state,
      @Nullable String sessionId) {
    Objects.requireNonNull(appName, "appName must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    String resolvedSessionId =
        (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;

    return Single.fromCallable(
        () ->
            transactionTemplate.execute(
                status -> {
                  if (sessionDao.exists(appName, userId, resolvedSessionId)) {
                    throw new SessionException(
                        "Session with id " + resolvedSessionId + " already exists.");
                  }

                  StateDelta delta = StateDeltaUtils.extract(state);
                  ensureAppStateExists(appName, delta.appState());
                  ensureUserStateExists(appName, userId, delta.userState());

                  Instant now = Instant.now();
                  String stateJson = JsonUtils.toJson(delta.sessionState());
                  sessionDao.insert(appName, userId, resolvedSessionId, stateJson, now, now);

                  Map<String, Object> merged =
                      StateDeltaUtils.merge(
                          getAppState(appName),
                          getUserState(appName, userId),
                          new ConcurrentHashMap<>(delta.sessionState()));
                  Session session =
                      Session.builder(resolvedSessionId)
                          .appName(appName)
                          .userId(userId)
                          .state(new State(new ConcurrentHashMap<>(merged)))
                          .lastUpdateTime(now)
                          .build();
                  return session;
                }));
  }

  // ----------------------------------------------------------------------------------------------
  // getSession
  // ----------------------------------------------------------------------------------------------

  @Override
  public Maybe<Session> getSession(
      String appName, String userId, String sessionId, Optional<GetSessionConfig> config) {
    Objects.requireNonNull(appName, "appName must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(config, "config must not be null");

    return Maybe.fromCallable(
        () -> {
          AdkSession row;
          try {
            row = sessionDao.findById(appName, userId, sessionId);
          } catch (SessionNotFoundException e) {
            return null; // Maybe.fromCallable(null) -> Maybe.empty()
          }

          Optional<Integer> numRecentEvents = config.flatMap(GetSessionConfig::numRecentEvents);
          List<Event> events;
          if (numRecentEvents.isPresent() && numRecentEvents.get() == 0) {
            events = Collections.emptyList();
          } else {
            Optional<Instant> afterTimestamp = config.flatMap(GetSessionConfig::afterTimestamp);
            events =
                eventDao.query(
                    appName, userId, sessionId, afterTimestamp, numRecentEvents.orElse(0));
          }

          Map<String, Object> appState = getAppState(appName);
          Map<String, Object> userState = getUserState(appName, userId);
          Map<String, Object> sessionState = JsonUtils.fromJson(row.state());
          Map<String, Object> merged = StateDeltaUtils.merge(appState, userState, sessionState);

          return Session.builder(sessionId)
              .appName(appName)
              .userId(userId)
              .state(new State(new ConcurrentHashMap<>(merged)))
              .events(new ArrayList<>(events))
              .lastUpdateTime(row.updateTime())
              .build();
        });
  }

  // ----------------------------------------------------------------------------------------------
  // listSessions
  // ----------------------------------------------------------------------------------------------

  @Override
  public Single<ListSessionsResponse> listSessions(String appName, String userId) {
    Objects.requireNonNull(appName, "appName must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    return Single.fromCallable(
        () -> {
          List<AdkSession> rows = sessionDao.findByAppAndUser(appName, userId);
          if (rows.isEmpty()) {
            return ListSessionsResponse.builder().build();
          }
          Map<String, Object> appState = getAppState(appName);
          Map<String, Object> userState = getUserState(appName, userId);

          List<Session> sessions = new ArrayList<>();
          for (AdkSession row : rows) {
            Map<String, Object> sessionState = JsonUtils.fromJson(row.state());
            Map<String, Object> merged = StateDeltaUtils.merge(appState, userState, sessionState);
            sessions.add(
                Session.builder(row.id())
                    .appName(row.appName())
                    .userId(row.userId())
                    .state(new State(new ConcurrentHashMap<>(merged)))
                    .events(new ArrayList<>())
                    .lastUpdateTime(row.updateTime())
                    .build());
          }
          return ListSessionsResponse.builder().sessions(sessions).build();
        });
  }

  // ----------------------------------------------------------------------------------------------
  // deleteSession
  // ----------------------------------------------------------------------------------------------

  @Override
  public Completable deleteSession(String appName, String userId, String sessionId) {
    Objects.requireNonNull(appName, "appName must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");

    return Completable.fromAction(
        () ->
            transactionTemplate.executeWithoutResult(
                status -> {
                  int deleted = sessionDao.delete(appName, userId, sessionId);
                  if (deleted == 0) {
                    throw new SessionNotFoundException(
                        "Session not found: " + appName + "/" + userId + "/" + sessionId);
                  }
                  // events are removed via ON DELETE CASCADE
                }));
  }

  // ----------------------------------------------------------------------------------------------
  // appendEvent
  // ----------------------------------------------------------------------------------------------

  @CanIgnoreReturnValue
  @Override
  public Single<Event> appendEvent(Session session, Event event) {
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(event, "event must not be null");
    Objects.requireNonNull(session.appName(), "session.appName must not be null");
    Objects.requireNonNull(session.userId(), "session.userId must not be null");
    Objects.requireNonNull(session.id(), "session.id must not be null");

    return Single.fromCallable(
        () -> {
          if (event.partial().orElse(false)) {
            return event;
          }

          // 1. Apply temp: state to the in-memory session (not persisted).
          applyTempState(session, event);

          // 2. Strip temp: keys from the persisted event's stateDelta.
          Event persistedEvent = trimTempDeltaState(event);

          // 3. Parse the (temp-stripped) stateDelta.
          EventActions actions = persistedEvent.actions();
          Map<String, Object> stateDelta = actions != null ? actions.stateDelta() : null;
          StateDelta delta = StateDeltaUtils.extract(stateDelta);

          // 4. Acquire the session lock.
          AdkSessionLock adkSessionLock =
              lockManager.acquireLock(session.appName(), session.userId(), session.id());
          try {
            // 5. Run the DB mutation inside a transaction.
            transactionTemplate.executeWithoutResult(
                status -> {
                  ensureAppStateExists(session.appName(), Collections.emptyMap());
                  ensureUserStateExists(
                      session.appName(), session.userId(), Collections.emptyMap());
                  applyStateDelta(session.appName(), session.userId(), session.id(), delta);
                  if (persistedEvent.id() == null) {
                    persistedEvent.setId(Event.generateEventId());
                  }
                  eventDao.insert(
                      persistedEvent, session.appName(), session.userId(), session.id());
                  sessionDao.updateUpdateTime(
                      session.appName(),
                      session.userId(),
                      session.id(),
                      Instant.ofEpochMilli(persistedEvent.timestamp()));
                });

            // 6. Update the in-memory session via the default super behavior.
            BaseSessionService.super.appendEvent(session, event).blockingSubscribe();
            session.lastUpdateTime(Instant.ofEpochMilli(persistedEvent.timestamp()));
            return event;
          } finally {
            lockManager.releaseLock(adkSessionLock);
          }
        });
  }

  // ----------------------------------------------------------------------------------------------
  // listEvents
  // ----------------------------------------------------------------------------------------------

  @Override
  public Single<ListEventsResponse> listEvents(String appName, String userId, String sessionId) {
    Objects.requireNonNull(appName, "appName must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");

    return Single.fromCallable(
        () -> {
          if (!sessionDao.exists(appName, userId, sessionId)) {
            throw new SessionNotFoundException(
                "Session not found: " + appName + "/" + userId + "/" + sessionId);
          }
          List<Event> events = eventDao.findBySession(appName, userId, sessionId);
          return ListEventsResponse.builder().events(events).build();
        });
  }

  // ----------------------------------------------------------------------------------------------
  // Helpers: state tables
  // ----------------------------------------------------------------------------------------------

  private void ensureAppStateExists(String appName, Map<String, Object> initialDelta) {
    if (!appStateDao.exists(appName)) {
      appStateDao.insert(appName, JsonUtils.toJson(initialDelta), Instant.now());
    }
  }

  private void ensureUserStateExists(
      String appName, String userId, Map<String, Object> initialDelta) {
    if (!userStateDao.exists(appName, userId)) {
      userStateDao.insert(appName, userId, JsonUtils.toJson(initialDelta), Instant.now());
    }
  }

  private Map<String, Object> getAppState(String appName) {
    return appStateDao.findState(appName).map(JsonUtils::fromJson).orElseGet(HashMap::new);
  }

  private Map<String, Object> getUserState(String appName, String userId) {
    return userStateDao.findState(appName, userId).map(JsonUtils::fromJson).orElseGet(HashMap::new);
  }

  private void applyStateDelta(String appName, String userId, String sessionId, StateDelta delta) {
    if (!delta.appState().isEmpty()) {
      Map<String, Object> currentState = getAppState(appName);
      applyDelta(currentState, delta.appState());
      appStateDao.updateState(appName, JsonUtils.toJson(currentState), Instant.now());
    }
    if (!delta.userState().isEmpty()) {
      Map<String, Object> currentState = getUserState(appName, userId);
      applyDelta(currentState, delta.userState());
      userStateDao.updateState(appName, userId, JsonUtils.toJson(currentState), Instant.now());
    }
    if (!delta.sessionState().isEmpty()) {
      AdkSession row = sessionDao.findById(appName, userId, sessionId);
      Map<String, Object> sessionState = JsonUtils.fromJson(row.state());
      applyDelta(sessionState, delta.sessionState());
      sessionDao.updateState(
          appName, userId, sessionId, JsonUtils.toJson(sessionState), Instant.now());
    }
  }

  private static void applyDelta(Map<String, Object> state, Map<String, Object> delta) {
    for (Map.Entry<String, Object> entry : delta.entrySet()) {
      if (entry.getValue() == State.REMOVED) {
        state.remove(entry.getKey());
      } else {
        state.put(entry.getKey(), entry.getValue());
      }
    }
  }

  private void applyTempState(Session session, Event event) {
    EventActions actions = event.actions();
    if (actions == null) {
      return;
    }
    Map<String, Object> stateDelta = actions.stateDelta();
    if (stateDelta == null) {
      return;
    }
    for (Map.Entry<String, Object> entry : stateDelta.entrySet()) {
      if (entry.getKey().startsWith(State.TEMP_PREFIX)) {
        if (entry.getValue() == State.REMOVED) {
          session.state().remove(entry.getKey());
        } else {
          session.state().put(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  private Event trimTempDeltaState(Event event) {
    EventActions actions = event.actions();
    if (actions == null) {
      return event;
    }
    Map<String, Object> originalDelta = actions.stateDelta();
    if (originalDelta == null || originalDelta.isEmpty()) {
      return event;
    }
    Map<String, Object> trimmed = new HashMap<>();
    for (Map.Entry<String, Object> entry : originalDelta.entrySet()) {
      if (!entry.getKey().startsWith(State.TEMP_PREFIX)) {
        trimmed.put(entry.getKey(), entry.getValue());
      }
    }
    EventActions newActions = EventActions.builder().stateDelta(trimmed).build();
    return event.toBuilder().actions(newActions).build();
  }
}
