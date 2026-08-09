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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.adk.sessions.SessionException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DialectDetector}. */
public class DialectDetectorTest {

  // ------------------------------------------------------------------------------------------------
  // fromProductName
  // ------------------------------------------------------------------------------------------------

  @Test
  void fromProductName_mysql_returnsMysql() {
    assertThat(DialectDetector.fromProductName("MySQL")).isEqualTo(DatabaseDialect.MYSQL);
  }

  @Test
  void fromProductName_postgresql_returnsPostgresql() {
    assertThat(DialectDetector.fromProductName("PostgreSQL")).isEqualTo(DatabaseDialect.POSTGRESQL);
  }

  @Test
  void fromProductName_oracle_returnsOracle() {
    assertThat(DialectDetector.fromProductName("Oracle")).isEqualTo(DatabaseDialect.ORACLE);
  }

  @Test
  void fromProductName_sqlite_returnsSqlite() {
    assertThat(DialectDetector.fromProductName("SQLite")).isEqualTo(DatabaseDialect.SQLITE);
  }

  @Test
  void fromProductName_h2_returnsH2() {
    assertThat(DialectDetector.fromProductName("H2")).isEqualTo(DatabaseDialect.H2);
  }

  @Test
  void fromProductName_isCaseInsensitiveAndMatchesSubstrings() {
    assertThat(DialectDetector.fromProductName("mysql community server"))
        .isEqualTo(DatabaseDialect.MYSQL);
    assertThat(DialectDetector.fromProductName("postgresql")).isEqualTo(DatabaseDialect.POSTGRESQL);
    assertThat(DialectDetector.fromProductName("h2 database")).isEqualTo(DatabaseDialect.H2);
  }

  @Test
  void fromProductName_unknownProduct_throwsSessionException() {
    SessionException ex =
        assertThrows(
            SessionException.class, () -> DialectDetector.fromProductName("DB2 Universal"));
    assertThat(ex.getMessage()).contains("Unsupported database dialect");
    assertThat(ex.getMessage()).contains("DB2 Universal");
  }

  // ------------------------------------------------------------------------------------------------
  // detect
  // ------------------------------------------------------------------------------------------------

  /** Detection reads the JDBC product name from a live connection and closes it afterwards. */
  @Test
  void detect_returnsDialectFromDatabaseMetadata() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getDatabaseProductName()).thenReturn("MySQL");

    DatabaseDialect dialect = DialectDetector.detect(dataSource);

    assertThat(dialect).isEqualTo(DatabaseDialect.MYSQL);
    verify(connection).close();
  }

  @Test
  void detect_connectionFailure_throwsSessionException() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

    SessionException ex =
        assertThrows(SessionException.class, () -> DialectDetector.detect(dataSource));

    assertThat(ex.getMessage()).contains("Failed to detect database dialect");
    assertThat(ex).hasCauseThat().isInstanceOf(SQLException.class);
  }

  // ------------------------------------------------------------------------------------------------
  // create
  // ------------------------------------------------------------------------------------------------

  @Test
  void create_mysql_returnsMySqlDialect() {
    SqlDialect dialect = DialectDetector.create(DatabaseDialect.MYSQL);
    assertThat(dialect).isInstanceOf(MySqlDialect.class);
    assertThat(dialect.dialect()).isEqualTo(DatabaseDialect.MYSQL);
  }

  @Test
  void create_sqlite_returnsSqliteDialect() {
    SqlDialect dialect = DialectDetector.create(DatabaseDialect.SQLITE);
    assertThat(dialect).isInstanceOf(SqliteDialect.class);
    assertThat(dialect.dialect()).isEqualTo(DatabaseDialect.SQLITE);
  }

  @Test
  void create_otherDialects_returnGenericSqlDialectWithMatchingDialect() {
    DatabaseDialect[] genericDialects = {
      DatabaseDialect.POSTGRESQL, DatabaseDialect.ORACLE, DatabaseDialect.H2
    };
    for (DatabaseDialect databaseDialect : genericDialects) {
      SqlDialect dialect = DialectDetector.create(databaseDialect);
      assertThat(dialect).isInstanceOf(GenericSqlDialect.class);
      assertThat(dialect.dialect()).isEqualTo(databaseDialect);
    }
  }
}
