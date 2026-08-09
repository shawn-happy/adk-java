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

package com.google.adk.util;

import com.google.adk.schema.DatabaseDialect;
import com.google.adk.sessions.SessionException;
import java.time.Instant;

/**
 * Adapts {@code Instant} to and from database timestamp representations.
 *
 * <p>All timestamps are stored as {@code BIGINT} epoch nanoseconds, which provides nanosecond
 * precision uniformly across MySQL, PostgreSQL, Oracle, SQLite, and H2. This avoids the microsecond
 * truncation inherent in {@code DATETIME(6)} / {@code TIMESTAMP(6)} columns on databases that do
 * not support 9 fractional digits.
 */
public final class TimestampUtils {

  private TimestampUtils() {}

  /**
   * Converts an {@code Instant} to epoch nanoseconds ({@code Long}).
   *
   * <p>The {@code dialect} parameter is retained for API compatibility but no longer affects the
   * output — all dialects use the same {@code BIGINT} representation.
   */
  public static Object toDbTimestamp(Instant instant, DatabaseDialect dialect) {
    return Math.addExact(
        Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
  }

  /**
   * Converts a database-returned value back to {@code Instant}.
   *
   * <p>The primary path is a {@code Number} (the epoch-nanos {@code BIGINT}). Fallbacks for {@code
   * Timestamp}, {@code Date}, {@code Instant}, and {@code String} are retained for robustness
   * against JDBC drivers that perform implicit type conversion.
   */
  public static Instant fromDbTimestamp(Object dbValue, DatabaseDialect dialect) {
    if (dbValue == null) {
      return Instant.EPOCH;
    }
    if (dbValue instanceof Number num) {
      long nanos = num.longValue();
      return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
    }
    if (dbValue instanceof java.sql.Timestamp ts) {
      return ts.toInstant();
    }
    if (dbValue instanceof java.util.Date date) {
      return date.toInstant();
    }
    if (dbValue instanceof Instant instant) {
      return instant;
    }
    if (dbValue instanceof String str) {
      try {
        return Instant.parse(str);
      } catch (Exception e) {
        throw new SessionException("Cannot parse timestamp string: " + str, e);
      }
    }
    throw new SessionException("Unexpected timestamp type: " + dbValue.getClass());
  }
}
