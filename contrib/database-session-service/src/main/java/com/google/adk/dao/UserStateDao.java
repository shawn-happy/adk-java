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

import com.google.adk.schema.SqlDialect;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Synchronous JDBC access for the {@code adk_user_states} table.
 *
 * <p>Mirrors {@link AppStateDao} with an additional {@code userId} column. {@link #findState}
 * returns {@link Optional#empty()} when no row exists. All methods participate in the caller's
 * Spring transaction.
 */
public final class UserStateDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final SqlDialect sqlDialect;

  public UserStateDao(NamedParameterJdbcTemplate jdbcTemplate, SqlDialect sqlDialect) {
    this.jdbcTemplate = jdbcTemplate;
    this.sqlDialect = sqlDialect;
  }

  /** Returns {@code true} if a user-state row exists for the given (app, user). */
  public boolean exists(String appName, String userId) {
    SqlParameterSource params =
        new MapSqlParameterSource().addValue("appName", appName).addValue("userId", userId);
    Integer count =
        jdbcTemplate.queryForObject(sqlDialect.userStateExistsSql(), params, Integer.class);
    return count != null && count > 0;
  }

  /** Inserts a new user-state row. */
  public void insert(String appName, String userId, String stateJson, Instant updateTime) {
    SqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("appName", appName)
            .addValue("userId", userId)
            .addValue("state", stateJson)
            .addValue("updateTime", sqlDialect.toDbTimestamp(updateTime));
    jdbcTemplate.update(sqlDialect.userStateInsertSql(), params);
  }

  /** Returns the JSON state for a (app, user), or empty if no row exists. */
  public Optional<String> findState(String appName, String userId) {
    SqlParameterSource params =
        new MapSqlParameterSource().addValue("appName", appName).addValue("userId", userId);
    List<String> results =
        jdbcTemplate.query(
            sqlDialect.userStateFindStateSql(), params, (rs, rowNum) -> rs.getString("state"));
    return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.get(0));
  }

  /** Updates the state and update_time for a (app, user). */
  public void updateState(String appName, String userId, String stateJson, Instant updateTime) {
    SqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("state", stateJson)
            .addValue("updateTime", sqlDialect.toDbTimestamp(updateTime))
            .addValue("appName", appName)
            .addValue("userId", userId);
    jdbcTemplate.update(sqlDialect.userStateUpdateStateSql(), params);
  }
}
