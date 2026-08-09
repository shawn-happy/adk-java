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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.adk.config.DataSourceConfig;
import com.google.adk.config.DatabaseConfig;
import com.google.adk.config.LockConfig;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.schema.DatabaseDialect;
import com.google.adk.schema.TestSchemaInitializer;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link DatabaseSessionService} against a real MySQL database.
 *
 * <p>Requires a reachable MySQL server. Connection settings default to {@code
 * jdbc:mysql://localhost:3306}, user {@code root}, password {@code 123456}, database {@code adk},
 * and can be overridden with the {@code adk.mysql.it.server-url}, {@code adk.mysql.it.database},
 * {@code adk.mysql.it.username} and {@code adk.mysql.it.password} system properties. The database
 * is created when missing, schema scripts are applied via {@link TestSchemaInitializer}, and all
 * {@code adk_*} tables are truncated before the first test. All tests are skipped when MySQL is not
 * available.
 */
public class DatabaseSessionServiceMySqlIntegrationTest {

  private static final String SERVER_URL =
      System.getProperty("adk.mysql.it.server-url", "jdbc:mysql://localhost:3306");
  private static final String DATABASE = System.getProperty("adk.mysql.it.database", "adk");
  private static final String USERNAME = System.getProperty("adk.mysql.it.username", "root");
  private static final String PASSWORD = System.getProperty("adk.mysql.it.password", "123456");

  private static DatabaseSessionService service;

  private static String jdbcUrl() {
    return SERVER_URL + "/" + DATABASE;
  }

  @BeforeAll
  static void setUp() {
    DriverManager.setLoginTimeout(3);
    try (Connection connection = DriverManager.getConnection(SERVER_URL, USERNAME, PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE IF NOT EXISTS " + DATABASE);
    } catch (SQLException e) {
      Assumptions.abort("MySQL is not available at " + SERVER_URL + ": " + e.getMessage());
    }

    DatabaseConfig config =
        DatabaseConfig.builder()
            .datasource(DataSourceConfig.of(jdbcUrl(), USERNAME, PASSWORD))
            .lock(LockConfig.database())
            .dialect("mysql")
            .build();
    DataSource dataSource = config.createDataSource();
    TestSchemaInitializer.initialize(dataSource, DatabaseDialect.MYSQL);
    service = new DatabaseSessionService(dataSource, config.createLockManager(dataSource));
    truncateAdkTables();
  }

  private static void truncateAdkTables() {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), USERNAME, PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("SET FOREIGN_KEY_CHECKS = 0");
      statement.execute("TRUNCATE TABLE adk_events");
      statement.execute("TRUNCATE TABLE adk_sessions");
      statement.execute("TRUNCATE TABLE adk_app_states");
      statement.execute("TRUNCATE TABLE adk_user_states");
      statement.execute("TRUNCATE TABLE adk_session_lock");
      statement.execute("SET FOREIGN_KEY_CHECKS = 1");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to truncate adk_* tables", e);
    }
  }

  // ----------------------------------------------------------------------------------------------
  // Fixtures
  // ----------------------------------------------------------------------------------------------

  private static String newAppName() {
    return "app-" + UUID.randomUUID();
  }

  private static String newUserId() {
    return "user-" + UUID.randomUUID();
  }

  private static String newSessionId() {
    return "session-" + UUID.randomUUID();
  }

  private static Session createSession(String appName, String userId) {
    return service.createSession(appName, userId, null, null).blockingGet();
  }

  private static Session getSession(String appName, String userId, String sessionId) {
    return service.getSession(appName, userId, sessionId, Optional.empty()).blockingGet();
  }

  private static Event newUserEvent(String invocationId) {
    return Event.builder()
        .author("user")
        .invocationId(invocationId)
        .content(Content.fromParts(Part.fromText("hello")))
        .build();
  }

  private static Event newEventWithDelta(String invocationId, Map<String, Object> stateDelta) {
    return Event.builder()
        .author("test-agent")
        .invocationId(invocationId)
        .actions(EventActions.builder().stateDelta(stateDelta).build())
        .build();
  }

  /** Appends {@code count} events with content {@code event-i} spaced one second apart. */
  private static void appendEventsAt(Session session, int count, long baseTimestampMillis) {
    for (int i = 0; i < count; i++) {
      Event event =
          Event.builder()
              .author("user")
              .invocationId("invocation-" + i)
              .content(Content.fromParts(Part.fromText("event-" + i)))
              .timestamp(baseTimestampMillis + i * 1000L)
              .build();
      service.appendEvent(session, event).blockingGet();
    }
  }

  private static String contentText(Event event) {
    return event.content().orElseThrow().parts().orElseThrow().get(0).text().orElseThrow();
  }

  // ----------------------------------------------------------------------------------------------
  // createSession
  // ----------------------------------------------------------------------------------------------

  /** Creating a session without an id generates one and persists the session. */
  @Test
  void createSession_nullSessionId_generatesIdAndPersistsSession() {
    String appName = newAppName();
    String userId = newUserId();

    Session created = service.createSession(appName, userId, null, null).blockingGet();

    assertThat(created.id()).isNotEmpty();
    assertThat(created.appName()).isEqualTo(appName);
    assertThat(created.userId()).isEqualTo(userId);
    assertThat(getSession(appName, userId, created.id()).id()).isEqualTo(created.id());
  }

  /** Initial state is split by prefix and persisted to app, user, and session state. */
  @Test
  void createSession_initialState_persistsAppUserAndSessionState() {
    String appName = newAppName();
    String userId = newUserId();
    Map<String, Object> initialState = new HashMap<>();
    initialState.put("app:theme", "dark");
    initialState.put("user:lang", "zh");
    initialState.put("counter", 1);

    Session created =
        service.createSession(appName, userId, initialState, newSessionId()).blockingGet();

    assertThat(created.state()).containsAtLeastEntriesIn(initialState);
    Session reloaded = getSession(appName, userId, created.id());
    assertThat(reloaded.state()).containsEntry("app:theme", "dark");
    assertThat(reloaded.state()).containsEntry("user:lang", "zh");
    assertThat(reloaded.state()).containsEntry("counter", 1);
  }

  /** temp: keys in the initial state are dropped and never persisted. */
  @Test
  void createSession_tempKeyInInitialState_notPersisted() {
    String appName = newAppName();
    String userId = newUserId();
    Map<String, Object> initialState = new HashMap<>();
    initialState.put("temp:token", "secret");
    initialState.put("visible", "value");

    Session created =
        service.createSession(appName, userId, initialState, newSessionId()).blockingGet();

    assertThat(created.state()).doesNotContainKey("temp:token");
    Session reloaded = getSession(appName, userId, created.id());
    assertThat(reloaded.state()).doesNotContainKey("temp:token");
    assertThat(reloaded.state()).containsEntry("visible", "value");
  }

  /** Creating a session with an already-used id fails. */
  @Test
  void createSession_duplicateSessionId_throwsSessionException() {
    String appName = newAppName();
    String userId = newUserId();
    String sessionId = newSessionId();
    service.createSession(appName, userId, null, sessionId).blockingGet();

    assertThrows(
        SessionException.class,
        () -> service.createSession(appName, userId, null, sessionId).blockingGet());
  }

  // ----------------------------------------------------------------------------------------------
  // getSession
  // ----------------------------------------------------------------------------------------------

  /** Getting an unknown session completes empty instead of failing. */
  @Test
  void getSession_unknownSession_returnsEmpty() {
    boolean empty =
        service
            .getSession(newAppName(), newUserId(), "missing-session", Optional.empty())
            .isEmpty()
            .blockingGet();

    assertThat(empty).isTrue();
  }

  /** Requesting zero recent events returns the session without any events. */
  @Test
  void getSession_zeroRecentEvents_returnsSessionWithoutEvents() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);
    appendEventsAt(session, 1, Instant.now().toEpochMilli());

    Session reloaded =
        service
            .getSession(
                appName,
                userId,
                session.id(),
                Optional.of(GetSessionConfig.builder().numRecentEvents(0).build()))
            .blockingGet();

    assertThat(reloaded.events()).isEmpty();
  }

  /** Requesting the N most recent events returns only the last N in ascending order. */
  @Test
  void getSession_numRecentEvents_returnsOnlyLastNEventsAscending() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);
    appendEventsAt(session, 3, Instant.now().toEpochMilli());

    Session reloaded =
        service
            .getSession(
                appName,
                userId,
                session.id(),
                Optional.of(GetSessionConfig.builder().numRecentEvents(2).build()))
            .blockingGet();

    assertThat(reloaded.events()).hasSize(2);
    assertThat(contentText(reloaded.events().get(0))).isEqualTo("event-1");
    assertThat(contentText(reloaded.events().get(1))).isEqualTo("event-2");
  }

  /** Filtering by afterTimestamp returns only events at or after that instant. */
  @Test
  void getSession_afterTimestamp_returnsOnlyEventsAtOrAfterTimestamp() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);
    long baseTimestamp = Instant.now().toEpochMilli();
    appendEventsAt(session, 3, baseTimestamp);

    Session reloaded =
        service
            .getSession(
                appName,
                userId,
                session.id(),
                Optional.of(
                    GetSessionConfig.builder()
                        .afterTimestamp(Instant.ofEpochMilli(baseTimestamp + 1000))
                        .build()))
            .blockingGet();

    assertThat(reloaded.events()).hasSize(2);
    assertThat(contentText(reloaded.events().get(0))).isEqualTo("event-1");
    assertThat(contentText(reloaded.events().get(1))).isEqualTo("event-2");
  }

  // ----------------------------------------------------------------------------------------------
  // listSessions
  // ----------------------------------------------------------------------------------------------

  /** Listing sessions returns only the sessions of the requested app and user. */
  @Test
  void listSessions_returnsOnlySessionsOfRequestedUser() {
    String appName = newAppName();
    String userId = newUserId();
    Session session1 = createSession(appName, userId);
    Session session2 = createSession(appName, userId);
    createSession(appName, newUserId()); // another user's session

    ListSessionsResponse response = service.listSessions(appName, userId).blockingGet();

    assertThat(response.sessionIds())
        .containsExactlyElementsIn(List.of(session1.id(), session2.id()));
  }

  /** Sessions returned by listSessions carry merged state but no events. */
  @Test
  void listSessions_sessionsHaveMergedStateAndNoEvents() {
    String appName = newAppName();
    String userId = newUserId();
    Map<String, Object> initialState = new HashMap<>();
    initialState.put("app:theme", "dark");
    initialState.put("counter", 1);
    Session created =
        service.createSession(appName, userId, initialState, newSessionId()).blockingGet();

    ListSessionsResponse response = service.listSessions(appName, userId).blockingGet();

    assertThat(response.sessions()).hasSize(1);
    Session listed = response.sessions().get(0);
    assertThat(listed.id()).isEqualTo(created.id());
    assertThat(listed.state()).containsEntry("app:theme", "dark");
    assertThat(listed.state()).containsEntry("counter", 1);
    assertThat(listed.events()).isEmpty();
  }

  // ----------------------------------------------------------------------------------------------
  // deleteSession
  // ----------------------------------------------------------------------------------------------

  /** Deleting a session removes it so getSession no longer finds it. */
  @Test
  void deleteSession_existingSession_sessionNoLongerFound() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);

    service.deleteSession(appName, userId, session.id()).blockingAwait();

    boolean empty =
        service.getSession(appName, userId, session.id(), Optional.empty()).isEmpty().blockingGet();
    assertThat(empty).isTrue();
  }

  /** Deleting a session cascades to its events. */
  @Test
  void deleteSession_sessionWithEvents_eventsAreDeletedToo() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);
    service.appendEvent(session, newUserEvent("invocation-1")).blockingGet();

    service.deleteSession(appName, userId, session.id()).blockingAwait();
    // Recreate a session with the same id to inspect the events table directly.
    service.createSession(appName, userId, null, session.id()).blockingGet();

    ListEventsResponse response = service.listEvents(appName, userId, session.id()).blockingGet();
    assertThat(response.events()).isEmpty();
  }

  /** Deleting an unknown session fails with SessionNotFoundException. */
  @Test
  void deleteSession_unknownSession_throwsSessionNotFoundException() {
    assertThrows(
        SessionNotFoundException.class,
        () -> service.deleteSession(newAppName(), newUserId(), "missing").blockingAwait());
  }

  // ----------------------------------------------------------------------------------------------
  // appendEvent
  // ----------------------------------------------------------------------------------------------

  /** Appending an event persists it and it can be listed back with its content. */
  @Test
  void appendEvent_validEvent_eventIsPersistedAndListed() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);
    Event event = newUserEvent("invocation-1");

    Event appended = service.appendEvent(session, event).blockingGet();

    List<Event> events = service.listEvents(appName, userId, session.id()).blockingGet().events();
    assertThat(events).hasSize(1);
    Event stored = events.get(0);
    assertThat(stored.id()).isEqualTo(appended.id());
    assertThat(stored.author()).isEqualTo("user");
    assertThat(stored.invocationId()).isEqualTo("invocation-1");
    assertThat(contentText(stored)).isEqualTo("hello");
  }

  /** Appending an event without an id generates one. */
  @Test
  void appendEvent_eventWithoutId_generatesId() {
    Session session = createSession(newAppName(), newUserId());
    Event event = newUserEvent("invocation-1");
    assertThat(event.id()).isNull();

    Event appended = service.appendEvent(session, event).blockingGet();

    assertThat(appended.id()).isNotNull();
  }

  /** Plain state delta keys are applied to the in-memory and persisted session state. */
  @Test
  void appendEvent_sessionStateDelta_updatesPersistedSessionState() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);

    service
        .appendEvent(session, newEventWithDelta("invocation-1", Map.of("counter", 42)))
        .blockingGet();

    assertThat(session.state()).containsEntry("counter", 42);
    assertThat(getSession(appName, userId, session.id()).state()).containsEntry("counter", 42);
  }

  /** app: and user: delta keys land in shared state visible to other sessions of the same user. */
  @Test
  void appendEvent_appAndUserStateDelta_isVisibleToOtherSessionsOfSameUser() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);
    Map<String, Object> delta = new HashMap<>();
    delta.put("app:theme", "dark");
    delta.put("user:lang", "zh");

    service.appendEvent(session, newEventWithDelta("invocation-1", delta)).blockingGet();

    assertThat(session.state()).containsEntry("app:theme", "dark");
    assertThat(session.state()).containsEntry("user:lang", "zh");
    Session otherSession = createSession(appName, userId);
    assertThat(otherSession.state()).containsEntry("app:theme", "dark");
    assertThat(otherSession.state()).containsEntry("user:lang", "zh");
  }

  /** temp: delta keys are applied in-memory only and stripped from the persisted event. */
  @Test
  void appendEvent_tempStateDelta_notPersisted() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);
    Map<String, Object> delta = new HashMap<>();
    delta.put("temp:token", "secret");
    delta.put("visible", "value");

    service.appendEvent(session, newEventWithDelta("invocation-1", delta)).blockingGet();

    assertThat(session.state()).containsEntry("temp:token", "secret");
    Session reloaded = getSession(appName, userId, session.id());
    assertThat(reloaded.state()).doesNotContainKey("temp:token");
    assertThat(reloaded.state()).containsEntry("visible", "value");
    List<Event> events = service.listEvents(appName, userId, session.id()).blockingGet().events();
    assertThat(events).hasSize(1);
    assertThat(events.get(0).actions().stateDelta()).doesNotContainKey("temp:token");
    assertThat(events.get(0).actions().stateDelta()).containsEntry("visible", "value");
  }

  /** A State.REMOVED value in the delta deletes the key from persisted session state. */
  @Test
  void appendEvent_removedSentinel_removesPersistedKey() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);
    service
        .appendEvent(session, newEventWithDelta("invocation-1", Map.of("counter", 42)))
        .blockingGet();
    Map<String, Object> removeDelta = new HashMap<>();
    removeDelta.put("counter", State.REMOVED);

    service.appendEvent(session, newEventWithDelta("invocation-2", removeDelta)).blockingGet();

    assertThat(session.state()).doesNotContainKey("counter");
    assertThat(getSession(appName, userId, session.id()).state()).doesNotContainKey("counter");
  }

  /** Partial events are returned as-is and neither the event nor its delta is persisted. */
  @Test
  void appendEvent_partialEvent_notPersisted() {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);
    Event partialEvent =
        Event.builder()
            .author("test-agent")
            .invocationId("invocation-1")
            .content(Content.fromParts(Part.fromText("partial")))
            .actions(EventActions.builder().stateDelta(Map.of("counter", 1)).build())
            .partial(true)
            .build();

    Event returned = service.appendEvent(session, partialEvent).blockingGet();

    assertThat(returned).isSameInstanceAs(partialEvent);
    assertThat(service.listEvents(appName, userId, session.id()).blockingGet().events()).isEmpty();
    assertThat(session.state()).doesNotContainKey("counter");
    assertThat(getSession(appName, userId, session.id()).state()).doesNotContainKey("counter");
  }

  /** Appending an event advances the in-memory session's last update time to the event time. */
  @Test
  void appendEvent_updatesInMemorySessionLastUpdateTime() {
    Session session = createSession(newAppName(), newUserId());
    long eventTimeMillis = Instant.now().toEpochMilli();
    Event event =
        Event.builder()
            .author("user")
            .invocationId("invocation-1")
            .timestamp(eventTimeMillis)
            .build();

    service.appendEvent(session, event).blockingGet();

    assertThat(session.lastUpdateTime().toEpochMilli()).isEqualTo(eventTimeMillis);
  }

  /** Concurrent appends to the same session are serialized by the lock and all persisted. */
  @Test
  void appendEvent_concurrentAppends_persistAllEvents() throws Exception {
    String appName = newAppName();
    String userId = newUserId();
    Session session = createSession(appName, userId);
    int eventCount = 8;
    ExecutorService executor = Executors.newFixedThreadPool(eventCount);
    try {
      List<Callable<Event>> tasks = new ArrayList<>();
      for (int i = 0; i < eventCount; i++) {
        final int index = i;
        tasks.add(
            () ->
                service
                    .appendEvent(
                        session,
                        newEventWithDelta(
                            "invocation-" + index, Map.of("key-" + index, "value-" + index)))
                    .blockingGet());
      }
      List<Future<Event>> futures = executor.invokeAll(tasks);
      for (Future<Event> future : futures) {
        future.get();
      }
    } finally {
      executor.shutdown();
    }

    List<Event> events = service.listEvents(appName, userId, session.id()).blockingGet().events();
    assertThat(events).hasSize(eventCount);
    Session reloaded = getSession(appName, userId, session.id());
    for (int i = 0; i < eventCount; i++) {
      assertThat(reloaded.state()).containsEntry("key-" + i, "value-" + i);
    }
  }

  // ----------------------------------------------------------------------------------------------
  // listEvents
  // ----------------------------------------------------------------------------------------------

  /** Listing events of an unknown session fails with SessionNotFoundException. */
  @Test
  void listEvents_unknownSession_throwsSessionNotFoundException() {
    assertThrows(
        SessionNotFoundException.class,
        () -> service.listEvents(newAppName(), newUserId(), "missing").blockingGet());
  }

  // ----------------------------------------------------------------------------------------------
  // Constructors
  // ----------------------------------------------------------------------------------------------

  /** The minimal constructor wires a working service with auto dialect and in-process locks. */
  @Test
  void minimalConstructor_createsWorkingService() {
    DatabaseSessionService minimalService =
        new DatabaseSessionService(jdbcUrl(), USERNAME, PASSWORD);
    String appName = newAppName();
    String userId = newUserId();

    Session session = minimalService.createSession(appName, userId, null, null).blockingGet();

    boolean empty =
        minimalService
            .getSession(appName, userId, session.id(), Optional.empty())
            .isEmpty()
            .blockingGet();
    assertThat(empty).isFalse();
  }
}
