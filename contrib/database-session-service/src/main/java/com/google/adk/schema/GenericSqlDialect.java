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

/**
 * Fallback {@link SqlDialect} for PostgreSQL, H2, and Oracle.
 *
 * <p>Delegates {@code LIMIT} / {@code FOR UPDATE} decisions to {@link DatabaseDialect} enum
 * methods, preserving the behavior previously encoded in {@code DialectSqlBuilder}.
 */
final class GenericSqlDialect implements SqlDialect {

  private final DatabaseDialect dialect;

  GenericSqlDialect(DatabaseDialect dialect) {
    this.dialect = dialect;
  }

  @Override
  public DatabaseDialect dialect() {
    return dialect;
  }

  @Override
  public String limitClause(int limit) {
    if (limit <= 0) {
      return "";
    }
    return switch (dialect) {
      case ORACLE -> "FETCH FIRST " + limit + " ROWS ONLY";
      default -> "LIMIT " + limit;
    };
  }
}
