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

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for the default methods of {@link SqlDialect}. */
public class SqlDialectTest {

  private static final Instant TEST_INSTANT = Instant.parse("2025-06-15T10:30:45.123456789Z");

  private final SqlDialect dialect = new MySqlDialect();

  // ------------------------------------------------------------------------------------------------
  // adk_sessions
  // ------------------------------------------------------------------------------------------------

  @Test
  void sessionExistsSql_returnsExpectedStatement() {
    assertThat(dialect.sessionExistsSql())
        .isEqualTo(
            "SELECT COUNT(*) FROM adk_sessions "
                + "WHERE app_name = :appName AND user_id = :userId AND id = :id");
  }

  @Test
  void sessionInsertSql_returnsExpectedStatement() {
    assertThat(dialect.sessionInsertSql())
        .isEqualTo(
            "INSERT INTO adk_sessions (app_name, user_id, id, state, create_time, update_time) "
                + "VALUES (:appName, :userId, :id, :state, :createTime, :updateTime)");
  }

  @Test
  void sessionFindByIdSql_returnsExpectedStatement() {
    assertThat(dialect.sessionFindByIdSql())
        .isEqualTo(
            "SELECT app_name, user_id, id, state, create_time, update_time "
                + "FROM adk_sessions WHERE app_name = :appName AND user_id = :userId AND id = :id");
  }

  @Test
  void sessionFindByAppAndUserSql_returnsExpectedStatement() {
    assertThat(dialect.sessionFindByAppAndUserSql())
        .isEqualTo(
            "SELECT app_name, user_id, id, state, create_time, update_time "
                + "FROM adk_sessions WHERE app_name = :appName AND user_id = :userId");
  }

  @Test
  void sessionDeleteSql_returnsExpectedStatement() {
    assertThat(dialect.sessionDeleteSql())
        .isEqualTo(
            "DELETE FROM adk_sessions "
                + "WHERE app_name = :appName AND user_id = :userId AND id = :id");
  }

  @Test
  void sessionUpdateUpdateTimeSql_returnsExpectedStatement() {
    assertThat(dialect.sessionUpdateUpdateTimeSql())
        .isEqualTo(
            "UPDATE adk_sessions SET update_time = :updateTime "
                + "WHERE app_name = :appName AND user_id = :userId AND id = :id");
  }

  @Test
  void sessionUpdateStateSql_returnsExpectedStatement() {
    assertThat(dialect.sessionUpdateStateSql())
        .isEqualTo(
            "UPDATE adk_sessions SET state = :state, update_time = :updateTime "
                + "WHERE app_name = :appName AND user_id = :userId AND id = :id");
  }

  // ------------------------------------------------------------------------------------------------
  // adk_events
  // ------------------------------------------------------------------------------------------------

  @Test
  void eventFindBySessionSql_returnsExpectedStatement() {
    assertThat(dialect.eventFindBySessionSql())
        .isEqualTo(
            "SELECT event_data FROM adk_events "
                + "WHERE app_name = :appName AND user_id = :userId AND session_id = :sessionId "
                + "ORDER BY timestamp ASC, id ASC");
  }

  @Test
  void eventQuerySql_noFilterNoLimit_omitsTimestampClauseAndLimit() {
    assertThat(dialect.eventQuerySql(false, 0))
        .isEqualTo(
            "SELECT event_data FROM adk_events "
                + "WHERE app_name = :appName AND user_id = :userId AND session_id = :sessionId "
                + "ORDER BY timestamp DESC, id DESC");
  }

  @Test
  void eventQuerySql_timestampFilter_appendsTimestampClause() {
    assertThat(dialect.eventQuerySql(true, 0))
        .isEqualTo(
            "SELECT event_data FROM adk_events "
                + "WHERE app_name = :appName AND user_id = :userId AND session_id = :sessionId "
                + "AND timestamp >= :afterTimestamp "
                + "ORDER BY timestamp DESC, id DESC");
  }

  @Test
  void eventQuerySql_limit_appendsDialectLimitClause() {
    assertThat(dialect.eventQuerySql(false, 10))
        .isEqualTo(
            "SELECT event_data FROM adk_events "
                + "WHERE app_name = :appName AND user_id = :userId AND session_id = :sessionId "
                + "ORDER BY timestamp DESC, id DESC LIMIT 10");
  }

  @Test
  void eventQuerySql_timestampFilterAndLimit_appendsBoth() {
    assertThat(dialect.eventQuerySql(true, 3))
        .isEqualTo(
            "SELECT event_data FROM adk_events "
                + "WHERE app_name = :appName AND user_id = :userId AND session_id = :sessionId "
                + "AND timestamp >= :afterTimestamp "
                + "ORDER BY timestamp DESC, id DESC LIMIT 3");
  }

  @Test
  void eventQuerySql_oracle_usesFetchFirstRowsOnly() {
    SqlDialect oracle = new GenericSqlDialect(DatabaseDialect.ORACLE);
    assertThat(oracle.eventQuerySql(false, 10))
        .isEqualTo(
            "SELECT event_data FROM adk_events "
                + "WHERE app_name = :appName AND user_id = :userId AND session_id = :sessionId "
                + "ORDER BY timestamp DESC, id DESC FETCH FIRST 10 ROWS ONLY");
  }

  @Test
  void eventInsertSql_returnsExpectedStatement() {
    assertThat(dialect.eventInsertSql())
        .isEqualTo(
            "INSERT INTO adk_events "
                + "(id, app_name, user_id, session_id, invocation_id, timestamp, event_data) "
                + "VALUES (:id, :appName, :userId, :sessionId, :invocationId, :timestamp,"
                + " :eventData)");
  }

  // ------------------------------------------------------------------------------------------------
  // adk_app_states
  // ------------------------------------------------------------------------------------------------

  @Test
  void appStateExistsSql_returnsExpectedStatement() {
    assertThat(dialect.appStateExistsSql())
        .isEqualTo("SELECT COUNT(*) FROM adk_app_states WHERE app_name = :appName");
  }

  @Test
  void appStateInsertSql_returnsExpectedStatement() {
    assertThat(dialect.appStateInsertSql())
        .isEqualTo(
            "INSERT INTO adk_app_states (app_name, state, update_time) "
                + "VALUES (:appName, :state, :updateTime)");
  }

  @Test
  void appStateFindStateSql_returnsExpectedStatement() {
    assertThat(dialect.appStateFindStateSql())
        .isEqualTo("SELECT state FROM adk_app_states WHERE app_name = :appName");
  }

  @Test
  void appStateUpdateStateSql_returnsExpectedStatement() {
    assertThat(dialect.appStateUpdateStateSql())
        .isEqualTo(
            "UPDATE adk_app_states SET state = :state, update_time = :updateTime "
                + "WHERE app_name = :appName");
  }

  // ------------------------------------------------------------------------------------------------
  // adk_user_states
  // ------------------------------------------------------------------------------------------------

  @Test
  void userStateExistsSql_returnsExpectedStatement() {
    assertThat(dialect.userStateExistsSql())
        .isEqualTo(
            "SELECT COUNT(*) FROM adk_user_states "
                + "WHERE app_name = :appName AND user_id = :userId");
  }

  @Test
  void userStateInsertSql_returnsExpectedStatement() {
    assertThat(dialect.userStateInsertSql())
        .isEqualTo(
            "INSERT INTO adk_user_states (app_name, user_id, state, update_time) "
                + "VALUES (:appName, :userId, :state, :updateTime)");
  }

  @Test
  void userStateFindStateSql_returnsExpectedStatement() {
    assertThat(dialect.userStateFindStateSql())
        .isEqualTo(
            "SELECT state FROM adk_user_states "
                + "WHERE app_name = :appName AND user_id = :userId");
  }

  @Test
  void userStateUpdateStateSql_returnsExpectedStatement() {
    assertThat(dialect.userStateUpdateStateSql())
        .isEqualTo(
            "UPDATE adk_user_states SET state = :state, update_time = :updateTime "
                + "WHERE app_name = :appName AND user_id = :userId");
  }

  // ------------------------------------------------------------------------------------------------
  // Timestamp conversion
  // ------------------------------------------------------------------------------------------------

  @Test
  void toDbTimestamp_returnsEpochNanosLong() {
    Object result = dialect.toDbTimestamp(TEST_INSTANT);
    assertThat(result).isInstanceOf(Long.class);
    assertThat((Long) result)
        .isEqualTo(TEST_INSTANT.getEpochSecond() * 1_000_000_000L + TEST_INSTANT.getNano());
  }

  @Test
  void fromDbTimestamp_roundTrip_preservesInstant() {
    for (SqlDialect candidate :
        new SqlDialect[] {
          new MySqlDialect(), new SqliteDialect(), new GenericSqlDialect(DatabaseDialect.ORACLE)
        }) {
      Object dbValue = candidate.toDbTimestamp(TEST_INSTANT);
      assertThat(candidate.fromDbTimestamp(dbValue)).isEqualTo(TEST_INSTANT);
    }
  }

  @Test
  void fromDbTimestamp_null_returnsEpoch() {
    assertThat(dialect.fromDbTimestamp(null)).isEqualTo(Instant.EPOCH);
  }
}
