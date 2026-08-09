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

import com.google.adk.sessions.SessionException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/** Detects the {@link DatabaseDialect} from a live {@link DataSource}. */
public final class DialectDetector {

  private DialectDetector() {}

  /** Detects the database dialect by inspecting {@code DatabaseMetaData}. */
  public static DatabaseDialect detect(DataSource dataSource) {
    try (Connection conn = dataSource.getConnection()) {
      String productName = conn.getMetaData().getDatabaseProductName();
      return fromProductName(productName);
    } catch (SQLException e) {
      throw new SessionException("Failed to detect database dialect", e);
    }
  }

  /** Maps a JDBC product name to a {@link DatabaseDialect}. */
  public static DatabaseDialect fromProductName(String productName) {
    String name = productName.toUpperCase();
    if (name.contains("MYSQL")) {
      return DatabaseDialect.MYSQL;
    }
    if (name.contains("POSTGRE")) {
      return DatabaseDialect.POSTGRESQL;
    }
    if (name.contains("ORACLE")) {
      return DatabaseDialect.ORACLE;
    }
    if (name.contains("SQLITE")) {
      return DatabaseDialect.SQLITE;
    }
    if (name.contains("H2")) {
      return DatabaseDialect.H2;
    }
    throw new SessionException("Unsupported database dialect: " + productName);
  }

  public static SqlDialect create(DatabaseDialect dialect) {
    return switch (dialect) {
      case MYSQL -> new MySqlDialect();
      case SQLITE -> new SqliteDialect();
      default -> new GenericSqlDialect(dialect);
    };
  }
}
