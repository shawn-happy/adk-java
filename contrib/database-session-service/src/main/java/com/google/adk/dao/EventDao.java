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

import com.google.adk.events.Event;
import com.google.adk.schema.SqlDialect;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Synchronous JDBC access for the {@code adk_events} table.
 *
 * <p>Handles JSON serialization/deserialization of {@link Event} objects. All methods participate
 * in the caller's Spring transaction.
 */
public final class EventDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final SqlDialect sqlDialect;

  public EventDao(NamedParameterJdbcTemplate jdbcTemplate, SqlDialect sqlDialect) {
    this.jdbcTemplate = jdbcTemplate;
    this.sqlDialect = sqlDialect;
  }

  /** Loads all events for a session, ordered ascending by {@code (timestamp, id)}. */
  public List<Event> findBySession(String appName, String userId, String sessionId) {
    SqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("appName", appName)
            .addValue("userId", userId)
            .addValue("sessionId", sessionId);
    List<String> jsonDataList =
        jdbcTemplate.queryForList(sqlDialect.eventFindBySessionSql(), params, String.class);
    List<Event> events = new ArrayList<>();
    for (String json : jsonDataList) {
      events.add(Event.fromJson(json));
    }
    return events;
  }

  /**
   * Loads events for a session, optionally filtered by {@code timestamp >= afterTimestamp} and
   * optionally limited to the most recent {@code limit} rows.
   *
   * <p>The SQL uses {@code ORDER BY timestamp DESC, id DESC}; the returned list is reversed to ASC
   * order for the caller. A {@code limit <= 0} means no limit.
   */
  public List<Event> query(
      String appName,
      String userId,
      String sessionId,
      Optional<Instant> afterTimestamp,
      int limit) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("appName", appName)
            .addValue("userId", userId)
            .addValue("sessionId", sessionId);

    boolean hasTimestampFilter = afterTimestamp.isPresent();
    if (hasTimestampFilter) {
      params.addValue("afterTimestamp", sqlDialect.toDbTimestamp(afterTimestamp.get()));
    }

    String sql = sqlDialect.eventQuerySql(hasTimestampFilter, limit);
    List<String> jsonDataList = jdbcTemplate.queryForList(sql, params, String.class);
    List<Event> events = new ArrayList<>();
    for (String json : jsonDataList) {
      events.add(Event.fromJson(json));
    }
    Collections.reverse(events); // DESC -> ASC for caller
    return events;
  }

  /** Inserts a new event row. The caller is responsible for ensuring {@code event.id()} is set. */
  public void insert(Event event, String appName, String userId, String sessionId) {
    String eventDataJson = event.toJson();
    Instant eventTime = Instant.ofEpochMilli(event.timestamp());
    SqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", event.id())
            .addValue("appName", appName)
            .addValue("userId", userId)
            .addValue("sessionId", sessionId)
            .addValue("invocationId", event.invocationId())
            .addValue("timestamp", sqlDialect.toDbTimestamp(eventTime))
            .addValue("eventData", eventDataJson);
    jdbcTemplate.update(sqlDialect.eventInsertSql(), params);
  }
}
