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

/** Unit tests for {@link GenericSqlDialect}. */
public class GenericSqlDialectTest {

  @Test
  void dialect_returnsConfiguredDialect() {
    assertThat(new GenericSqlDialect(DatabaseDialect.POSTGRESQL).dialect())
        .isEqualTo(DatabaseDialect.POSTGRESQL);
    assertThat(new GenericSqlDialect(DatabaseDialect.ORACLE).dialect())
        .isEqualTo(DatabaseDialect.ORACLE);
    assertThat(new GenericSqlDialect(DatabaseDialect.H2).dialect()).isEqualTo(DatabaseDialect.H2);
  }

  @Test
  void limitClause_postgresql_returnsLimit() {
    assertThat(new GenericSqlDialect(DatabaseDialect.POSTGRESQL).limitClause(5))
        .isEqualTo("LIMIT 5");
  }

  @Test
  void limitClause_h2_returnsLimit() {
    assertThat(new GenericSqlDialect(DatabaseDialect.H2).limitClause(5)).isEqualTo("LIMIT 5");
  }

  @Test
  void limitClause_oracle_returnsFetchFirstRowsOnly() {
    assertThat(new GenericSqlDialect(DatabaseDialect.ORACLE).limitClause(5))
        .isEqualTo("FETCH FIRST 5 ROWS ONLY");
  }

  @Test
  void limitClause_zero_returnsEmpty() {
    assertThat(new GenericSqlDialect(DatabaseDialect.POSTGRESQL).limitClause(0)).isEmpty();
    assertThat(new GenericSqlDialect(DatabaseDialect.ORACLE).limitClause(0)).isEmpty();
  }

  @Test
  void limitClause_negative_returnsEmpty() {
    assertThat(new GenericSqlDialect(DatabaseDialect.POSTGRESQL).limitClause(-1)).isEmpty();
    assertThat(new GenericSqlDialect(DatabaseDialect.ORACLE).limitClause(-1)).isEmpty();
  }
}
