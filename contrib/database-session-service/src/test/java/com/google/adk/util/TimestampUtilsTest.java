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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.adk.schema.DatabaseDialect;
import com.google.adk.sessions.SessionException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TimestampUtils}. */
public class TimestampUtilsTest {

  private static final Instant TEST_INSTANT = Instant.parse("2025-06-15T10:30:45.123456789Z");

  // ------------------------------------------------------------------------------------------------
  // toDbTimestamp
  // ------------------------------------------------------------------------------------------------

  @Test
  void toDbTimestamp_returnsLongEpochNanos() {
    for (DatabaseDialect dialect : DatabaseDialect.values()) {
      Object result = TimestampUtils.toDbTimestamp(TEST_INSTANT, dialect);
      assertThat(result).isInstanceOf(Long.class);
    }
  }

  @Test
  void toDbTimestamp_allDialectsProduceSameValue() {
    Object mysql = TimestampUtils.toDbTimestamp(TEST_INSTANT, DatabaseDialect.MYSQL);
    Object sqlite = TimestampUtils.toDbTimestamp(TEST_INSTANT, DatabaseDialect.SQLITE);
    Object pg = TimestampUtils.toDbTimestamp(TEST_INSTANT, DatabaseDialect.POSTGRESQL);
    Object oracle = TimestampUtils.toDbTimestamp(TEST_INSTANT, DatabaseDialect.ORACLE);
    Object h2 = TimestampUtils.toDbTimestamp(TEST_INSTANT, DatabaseDialect.H2);
    assertThat(mysql).isEqualTo(sqlite);
    assertThat(mysql).isEqualTo(pg);
    assertThat(mysql).isEqualTo(oracle);
    assertThat(mysql).isEqualTo(h2);
  }

  @Test
  void toDbTimestamp_preservesNanosecondPrecision() {
    long expectedNanos = TEST_INSTANT.getEpochSecond() * 1_000_000_000L + TEST_INSTANT.getNano();
    Object result = TimestampUtils.toDbTimestamp(TEST_INSTANT, DatabaseDialect.MYSQL);
    assertThat((Long) result).isEqualTo(expectedNanos);
  }

  @Test
  void toDbTimestamp_epochInstant_returnsZero() {
    Object result = TimestampUtils.toDbTimestamp(Instant.EPOCH, DatabaseDialect.MYSQL);
    assertThat((Long) result).isEqualTo(0L);
  }

  // ------------------------------------------------------------------------------------------------
  // fromDbTimestamp - null
  // ------------------------------------------------------------------------------------------------

  @Test
  void fromDbTimestamp_null_returnsEpoch() {
    for (DatabaseDialect dialect : DatabaseDialect.values()) {
      assertThat(TimestampUtils.fromDbTimestamp(null, dialect)).isEqualTo(Instant.EPOCH);
    }
  }

  // ------------------------------------------------------------------------------------------------
  // fromDbTimestamp - Number (primary path: epoch nanos BIGINT)
  // ------------------------------------------------------------------------------------------------

  @Test
  void fromDbTimestamp_longEpochNanos_returnsCorrectInstant() {
    long nanos = TEST_INSTANT.getEpochSecond() * 1_000_000_000L + TEST_INSTANT.getNano();
    Instant result = TimestampUtils.fromDbTimestamp(nanos, DatabaseDialect.MYSQL);
    assertThat(result).isEqualTo(TEST_INSTANT);
  }

  @Test
  void fromDbTimestamp_integerValue_returnsCorrectInstant() {
    // JDBC drivers for SQLite may return Integer instead of Long for small BIGINT values
    Instant result = TimestampUtils.fromDbTimestamp(0, DatabaseDialect.SQLITE);
    assertThat(result).isEqualTo(Instant.EPOCH);
    Instant result2 = TimestampUtils.fromDbTimestamp(1_000_000_000, DatabaseDialect.SQLITE);
    assertThat(result2).isEqualTo(Instant.ofEpochSecond(1, 0));
  }

  @Test
  void fromDbTimestamp_zeroLong_returnsEpoch() {
    Instant result = TimestampUtils.fromDbTimestamp(0L, DatabaseDialect.MYSQL);
    assertThat(result).isEqualTo(Instant.EPOCH);
  }

  // ------------------------------------------------------------------------------------------------
  // fromDbTimestamp - fallback types
  // ------------------------------------------------------------------------------------------------

  @Test
  void fromDbTimestamp_sqlTimestamp_returnsCorrectInstant() {
    java.sql.Timestamp ts = java.sql.Timestamp.from(TEST_INSTANT);
    Instant result = TimestampUtils.fromDbTimestamp(ts, DatabaseDialect.MYSQL);
    assertThat(result).isEqualTo(TEST_INSTANT);
  }

  @Test
  void fromDbTimestamp_utilDate_returnsCorrectInstant() {
    // java.util.Date only has millisecond precision
    Instant millisInstant = Instant.parse("2025-06-15T10:30:45.123Z");
    java.util.Date date = java.util.Date.from(millisInstant);
    Instant result = TimestampUtils.fromDbTimestamp(date, DatabaseDialect.MYSQL);
    assertThat(result).isEqualTo(millisInstant);
  }

  @Test
  void fromDbTimestamp_instant_returnsSameInstant() {
    Instant result = TimestampUtils.fromDbTimestamp(TEST_INSTANT, DatabaseDialect.MYSQL);
    assertThat(result).isSameInstanceAs(TEST_INSTANT);
  }

  @Test
  void fromDbTimestamp_validIsoString_returnsCorrectInstant() {
    String isoString = "2025-06-15T10:30:45.123456789Z";
    Instant result = TimestampUtils.fromDbTimestamp(isoString, DatabaseDialect.SQLITE);
    assertThat(result).isEqualTo(TEST_INSTANT);
  }

  @Test
  void fromDbTimestamp_invalidString_throwsSessionException() {
    String badString = "not-a-timestamp";
    SessionException ex =
        assertThrows(
            SessionException.class,
            () -> TimestampUtils.fromDbTimestamp(badString, DatabaseDialect.SQLITE));
    assertThat(ex.getMessage()).contains("Cannot parse timestamp string");
    assertThat(ex.getMessage()).contains("not-a-timestamp");
  }

  @Test
  void fromDbTimestamp_unknownType_throwsSessionException() {
    Object unknown = new Object();
    SessionException ex =
        assertThrows(
            SessionException.class,
            () -> TimestampUtils.fromDbTimestamp(unknown, DatabaseDialect.MYSQL));
    assertThat(ex.getMessage()).contains("Unexpected timestamp type");
  }

  // ------------------------------------------------------------------------------------------------
  // Round-trip
  // ------------------------------------------------------------------------------------------------

  @Test
  void toDbTimestamp_thenFromDbTimestamp_roundTrip_allDialects() {
    for (DatabaseDialect dialect : DatabaseDialect.values()) {
      Object dbValue = TimestampUtils.toDbTimestamp(TEST_INSTANT, dialect);
      Instant result = TimestampUtils.fromDbTimestamp(dbValue, dialect);
      assertThat(result).isEqualTo(TEST_INSTANT);
    }
  }

  @Test
  void toDbTimestamp_thenFromDbTimestamp_roundTrip_epochInstant() {
    for (DatabaseDialect dialect : DatabaseDialect.values()) {
      Object dbValue = TimestampUtils.toDbTimestamp(Instant.EPOCH, dialect);
      Instant result = TimestampUtils.fromDbTimestamp(dbValue, dialect);
      assertThat(result).isEqualTo(Instant.EPOCH);
    }
  }

  @Test
  void toDbTimestamp_thenFromDbTimestamp_roundTrip_nowInstant() {
    Instant now = Instant.now();
    for (DatabaseDialect dialect : DatabaseDialect.values()) {
      Object dbValue = TimestampUtils.toDbTimestamp(now, dialect);
      Instant result = TimestampUtils.fromDbTimestamp(dbValue, dialect);
      assertThat(result).isEqualTo(now);
    }
  }

  @Test
  void roundTrip_preservesFullNanosecondPrecision() {
    // An instant with nanosecond precision that would be truncated by DATETIME(6)/TIMESTAMP(6)
    Instant instant = Instant.parse("2025-01-01T00:00:00.123456789Z");
    Object dbValue = TimestampUtils.toDbTimestamp(instant, DatabaseDialect.MYSQL);
    Instant result = TimestampUtils.fromDbTimestamp(dbValue, DatabaseDialect.MYSQL);
    assertThat(result).isEqualTo(instant);
    // Verify the nanos are exactly preserved, not truncated to micros
    assertThat(result.getNano()).isEqualTo(123456789);
  }

  @Test
  void roundTrip_microsecondPrecisionInstant_preservedExactly() {
    Instant instant = Instant.parse("2025-01-01T00:00:00.123456Z");
    Object dbValue = TimestampUtils.toDbTimestamp(instant, DatabaseDialect.POSTGRESQL);
    Instant result = TimestampUtils.fromDbTimestamp(dbValue, DatabaseDialect.POSTGRESQL);
    assertThat(result).isEqualTo(instant);
  }

  @Test
  void toDbTimestamp_ordersCorrectlyAsBigint() {
    Instant earlier = Instant.parse("2025-01-01T00:00:00Z");
    Instant later = Instant.parse("2025-01-01T00:00:00.000000001Z"); // 1 nanosecond later
    Long earlierNanos = (Long) TimestampUtils.toDbTimestamp(earlier, DatabaseDialect.MYSQL);
    Long laterNanos = (Long) TimestampUtils.toDbTimestamp(later, DatabaseDialect.MYSQL);
    assertThat(laterNanos).isGreaterThan(earlierNanos);
    assertThat(laterNanos - earlierNanos).isEqualTo(1L);
  }
}
