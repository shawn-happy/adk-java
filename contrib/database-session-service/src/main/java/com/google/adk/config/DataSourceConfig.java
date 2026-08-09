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

package com.google.adk.config;

import org.jspecify.annotations.Nullable;

/** HikariCP-backed {@code DataSource} configuration. */
public record DataSourceConfig(
    String url,
    @Nullable String username,
    @Nullable String password,
    @Nullable String driverClassName,
    int maximumPoolSize,
    long connectionTimeoutMs,
    long idleTimeoutMs,
    long maxLifetimeMs) {

  public DataSourceConfig {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("DataSource url must not be null or blank");
    }
  }

  /** Minimal builder: url + credentials, with Hikari defaults. */
  public static DataSourceConfig of(String url, String username, String password) {
    return new DataSourceConfig(url, username, password, null, 10, 30_000L, 600_000L, 1_800_000L);
  }

  /** SQLite-specific config (single connection, foreign keys on). */
  public static DataSourceConfig sqlite(String url) {
    return new DataSourceConfig(
        url, null, null, "org.sqlite.JDBC", 1, 30_000L, 600_000L, 1_800_000L);
  }
}
