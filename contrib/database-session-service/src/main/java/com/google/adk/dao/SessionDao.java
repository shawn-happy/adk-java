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

package com.google.adk.dao;

import com.google.adk.entity.AdkSession;
import com.google.adk.schema.SqlDialect;
import com.google.adk.sessions.SessionNotFoundException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Synchronous JDBC access for the {@code adk_sessions} table.
 *
 * <p>All methods participate in the caller's Spring transaction (via the shared {@code
 * NamedParameterJdbcTemplate}'s {@code DataSource}). {@link #findById} throws {@link
 * SessionNotFoundException} on miss; other methods return counts or void.
 */
public final class SessionDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final SqlDialect sqlDialect;

  private final RowMapper<AdkSession> rowMapper;

  public SessionDao(NamedParameterJdbcTemplate jdbcTemplate, SqlDialect sqlDialect) {
    this.jdbcTemplate = jdbcTemplate;
    this.sqlDialect = sqlDialect;
    this.rowMapper =
        (rs, rowNum) ->
            new AdkSession(
                rs.getString("app_name"),
                rs.getString("user_id"),
                rs.getString("id"),
                rs.getString("state"),
                sqlDialect.fromDbTimestamp(rs.getObject("create_time")),
                sqlDialect.fromDbTimestamp(rs.getObject("update_time")));
  }

  /** Returns {@code true} if a session row exists for the given key. */
  public boolean exists(String appName, String userId, String sessionId) {
    SqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("appName", appName)
            .addValue("userId", userId)
            .addValue("id", sessionId);
    Integer count =
        jdbcTemplate.queryForObject(sqlDialect.sessionExistsSql(), params, Integer.class);
    return count != null && count > 0;
  }

  /** Inserts a new session row. */
  public void insert(
      String appName,
      String userId,
      String sessionId,
      String stateJson,
      Instant createTime,
      Instant updateTime) {
    SqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("appName", appName)
            .addValue("userId", userId)
            .addValue("id", sessionId)
            .addValue("state", stateJson)
            .addValue("createTime", sqlDialect.toDbTimestamp(createTime))
            .addValue("updateTime", sqlDialect.toDbTimestamp(updateTime));
    jdbcTemplate.update(sqlDialect.sessionInsertSql(), params);
  }

  /**
   * Loads a single session row by key.
   *
   * @throws SessionNotFoundException if no row matches
   */
  public AdkSession findById(String appName, String userId, String sessionId) {
    SqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("appName", appName)
            .addValue("userId", userId)
            .addValue("id", sessionId);
    List<AdkSession> rows = jdbcTemplate.query(sqlDialect.sessionFindByIdSql(), params, rowMapper);
    if (rows.isEmpty()) {
      throw new SessionNotFoundException(
          "Session not found: " + appName + "/" + userId + "/" + sessionId);
    }
    return rows.get(0);
  }

  /** Loads all session rows for a given (app, user). */
  public List<AdkSession> findByAppAndUser(String appName, String userId) {
    SqlParameterSource params =
        new MapSqlParameterSource().addValue("appName", appName).addValue("userId", userId);
    return jdbcTemplate.query(sqlDialect.sessionFindByAppAndUserSql(), params, rowMapper);
  }

  /**
   * Deletes a single session row.
   *
   * @return number of rows deleted (0 if none)
   */
  public int delete(String appName, String userId, String sessionId) {
    SqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("appName", appName)
            .addValue("userId", userId)
            .addValue("id", sessionId);
    return jdbcTemplate.update(sqlDialect.sessionDeleteSql(), params);
  }

  /** Updates the {@code update_time} column of a session row. */
  public void updateUpdateTime(
      String appName, String userId, String sessionId, Instant updateTime) {
    SqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("updateTime", sqlDialect.toDbTimestamp(updateTime))
            .addValue("appName", appName)
            .addValue("userId", userId)
            .addValue("id", sessionId);
    jdbcTemplate.update(sqlDialect.sessionUpdateUpdateTimeSql(), params);
  }

  /** Updates the {@code state} and {@code update_time} columns of a session row. */
  public void updateState(
      String appName, String userId, String sessionId, String stateJson, Instant updateTime) {
    SqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("state", stateJson)
            .addValue("updateTime", sqlDialect.toDbTimestamp(updateTime))
            .addValue("appName", appName)
            .addValue("userId", userId)
            .addValue("id", sessionId);
    jdbcTemplate.update(sqlDialect.sessionUpdateStateSql(), params);
  }
}
