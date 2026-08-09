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

package com.google.adk.schema;

import com.google.adk.util.TimestampUtils;
import java.time.Instant;

/**
 * Provides dialect-specific SQL statements for {@code DatabaseSessionService} table access.
 *
 * <p>All SQL uses named parameters (e.g. {@code :appName}) compatible with Spring's {@code
 * NamedParameterJdbcTemplate}. The ~95% of SQL that is identical across dialects is implemented as
 * {@code default} methods; subclasses only override {@link #dialect()} and {@link
 * #limitClause(int)}.
 *
 * <p>Timestamp conversion is centralized here by delegating to {@link TimestampUtils}, so DAOs
 * never reference {@link DatabaseDialect} or {@code TimestampUtils} directly.
 */
public interface SqlDialect {

  /** Returns the database dialect this instance is configured for. */
  DatabaseDialect dialect();

  /**
   * Returns the dialect-specific SQL fragment for limiting to {@code limit} rows (empty if none).
   */
  String limitClause(int limit);

  // ----------------------------------------------------------------------------------------------
  // adk_sessions
  // ----------------------------------------------------------------------------------------------

  /** {@code SELECT COUNT(*)} existence check for a single session row. */
  default String sessionExistsSql() {
    return "SELECT COUNT(*) FROM adk_sessions "
        + "WHERE app_name = :appName AND user_id = :userId AND id = :id";
  }

  /** {@code INSERT} a new session row. */
  default String sessionInsertSql() {
    return "INSERT INTO adk_sessions (app_name, user_id, id, state, create_time, update_time) "
        + "VALUES (:appName, :userId, :id, :state, :createTime, :updateTime)";
  }

  /** {@code SELECT} a single session by (app, user, id) without row-level lock. */
  default String sessionFindByIdSql() {
    return "SELECT app_name, user_id, id, state, create_time, update_time "
        + "FROM adk_sessions WHERE app_name = :appName AND user_id = :userId AND id = :id";
  }

  /** {@code SELECT} all sessions for a given (app, user). */
  default String sessionFindByAppAndUserSql() {
    return "SELECT app_name, user_id, id, state, create_time, update_time "
        + "FROM adk_sessions WHERE app_name = :appName AND user_id = :userId";
  }

  /** {@code DELETE} a single session row. */
  default String sessionDeleteSql() {
    return "DELETE FROM adk_sessions "
        + "WHERE app_name = :appName AND user_id = :userId AND id = :id";
  }

  /** {@code UPDATE} the {@code update_time} column of a session row. */
  default String sessionUpdateUpdateTimeSql() {
    return "UPDATE adk_sessions SET update_time = :updateTime "
        + "WHERE app_name = :appName AND user_id = :userId AND id = :id";
  }

  /** {@code UPDATE} the {@code state} and {@code update_time} columns of a session row. */
  default String sessionUpdateStateSql() {
    return "UPDATE adk_sessions SET state = :state, update_time = :updateTime "
        + "WHERE app_name = :appName AND user_id = :userId AND id = :id";
  }

  // ----------------------------------------------------------------------------------------------
  // adk_events
  // ----------------------------------------------------------------------------------------------

  /** {@code SELECT event_data} ordered ascending for a session (used by {@code listEvents}). */
  default String eventFindBySessionSql() {
    return "SELECT event_data FROM adk_events "
        + "WHERE app_name = :appName AND user_id = :userId AND session_id = :sessionId "
        + "ORDER BY timestamp ASC, id ASC";
  }

  /**
   * Builds the dynamic {@code SELECT event_data} statement for {@code getSession}.
   *
   * @param hasTimestampFilter whether to append {@code AND timestamp >= :afterTimestamp}
   * @param limit row limit ({@code <= 0} means no limit)
   */
  default String eventQuerySql(boolean hasTimestampFilter, int limit) {
    StringBuilder sql = new StringBuilder("SELECT event_data FROM adk_events ");
    sql.append("WHERE app_name = :appName AND user_id = :userId AND session_id = :sessionId ");
    if (hasTimestampFilter) {
      sql.append("AND timestamp >= :afterTimestamp ");
    }
    sql.append("ORDER BY timestamp DESC, id DESC");
    String clause = limitClause(limit);
    if (!clause.isEmpty()) {
      sql.append(' ').append(clause);
    }
    return sql.toString();
  }

  /** {@code INSERT} a new event row. */
  default String eventInsertSql() {
    return "INSERT INTO adk_events "
        + "(id, app_name, user_id, session_id, invocation_id, timestamp, event_data) "
        + "VALUES (:id, :appName, :userId, :sessionId, :invocationId, :timestamp, :eventData)";
  }

  // ----------------------------------------------------------------------------------------------
  // adk_app_states
  // ----------------------------------------------------------------------------------------------

  /** {@code SELECT COUNT(*)} existence check for an app-state row. */
  default String appStateExistsSql() {
    return "SELECT COUNT(*) FROM adk_app_states WHERE app_name = :appName";
  }

  /** {@code INSERT} a new app-state row. */
  default String appStateInsertSql() {
    return "INSERT INTO adk_app_states (app_name, state, update_time) "
        + "VALUES (:appName, :state, :updateTime)";
  }

  /** {@code SELECT state} for an app. */
  default String appStateFindStateSql() {
    return "SELECT state FROM adk_app_states WHERE app_name = :appName";
  }

  /** {@code UPDATE state} and {@code update_time} for an app. */
  default String appStateUpdateStateSql() {
    return "UPDATE adk_app_states SET state = :state, update_time = :updateTime "
        + "WHERE app_name = :appName";
  }

  // ----------------------------------------------------------------------------------------------
  // adk_user_states
  // ----------------------------------------------------------------------------------------------

  /** {@code SELECT COUNT(*)} existence check for a user-state row. */
  default String userStateExistsSql() {
    return "SELECT COUNT(*) FROM adk_user_states "
        + "WHERE app_name = :appName AND user_id = :userId";
  }

  /** {@code INSERT} a new user-state row. */
  default String userStateInsertSql() {
    return "INSERT INTO adk_user_states (app_name, user_id, state, update_time) "
        + "VALUES (:appName, :userId, :state, :updateTime)";
  }

  /** {@code SELECT state} for a (app, user). */
  default String userStateFindStateSql() {
    return "SELECT state FROM adk_user_states " + "WHERE app_name = :appName AND user_id = :userId";
  }

  /** {@code UPDATE state} and {@code update_time} for a (app, user). */
  default String userStateUpdateStateSql() {
    return "UPDATE adk_user_states SET state = :state, update_time = :updateTime "
        + "WHERE app_name = :appName AND user_id = :userId";
  }

  // ----------------------------------------------------------------------------------------------
  // Timestamp conversion
  // ----------------------------------------------------------------------------------------------

  /** Converts an {@code Instant} to the database-specific timestamp representation. */
  default Object toDbTimestamp(Instant instant) {
    return TimestampUtils.toDbTimestamp(instant, dialect());
  }

  /** Converts a database-returned timestamp object back to {@code Instant}. */
  default Instant fromDbTimestamp(Object dbValue) {
    return TimestampUtils.fromDbTimestamp(dbValue, dialect());
  }
}
