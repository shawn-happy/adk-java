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

import org.junit.jupiter.api.Test;

/** Unit tests for {@link DatabaseDialect}. */
public class DatabaseDialectTest {

  @Test
  void supportsForeignKeyCascade_nonSqliteDialects_returnsTrue() {
    assertThat(DatabaseDialect.MYSQL.supportsForeignKeyCascade()).isTrue();
    assertThat(DatabaseDialect.POSTGRESQL.supportsForeignKeyCascade()).isTrue();
    assertThat(DatabaseDialect.ORACLE.supportsForeignKeyCascade()).isTrue();
    assertThat(DatabaseDialect.H2.supportsForeignKeyCascade()).isTrue();
  }

  @Test
  void supportsForeignKeyCascade_sqlite_returnsFalse() {
    // SQLite requires PRAGMA foreign_keys = ON per connection, so cascade is not enforced.
    assertThat(DatabaseDialect.SQLITE.supportsForeignKeyCascade()).isFalse();
  }
}
