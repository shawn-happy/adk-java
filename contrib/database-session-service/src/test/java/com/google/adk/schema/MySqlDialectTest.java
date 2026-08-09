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

/** Unit tests for {@link MySqlDialect}. */
public class MySqlDialectTest {

  private final MySqlDialect dialect = new MySqlDialect();

  @Test
  void dialect_returnsMysql() {
    assertThat(dialect.dialect()).isEqualTo(DatabaseDialect.MYSQL);
  }

  @Test
  void limitClause_positiveLimit_returnsLimitClause() {
    assertThat(dialect.limitClause(10)).isEqualTo("LIMIT 10");
  }

  @Test
  void limitClause_zero_returnsEmpty() {
    assertThat(dialect.limitClause(0)).isEmpty();
  }

  @Test
  void limitClause_negative_returnsEmpty() {
    assertThat(dialect.limitClause(-5)).isEmpty();
  }
}
