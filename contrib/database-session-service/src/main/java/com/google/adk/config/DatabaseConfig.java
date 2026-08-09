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

import com.google.adk.lock.LockManager;
import com.google.adk.lock.LockManagerFactory;
import com.google.adk.schema.DatabaseDialect;
import com.google.adk.schema.DialectDetector;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/** Top-level configuration for {@code DatabaseSessionService}. */
public final class DatabaseConfig {

  private final DataSourceConfig datasource;
  private final LockConfig lock;
  private final String dialect;

  private DatabaseConfig(Builder b) {
    this.datasource = b.datasource;
    this.lock = b.lock;
    this.dialect = b.dialect;
  }

  public DataSourceConfig datasource() {
    return datasource;
  }

  public LockConfig lock() {
    return lock;
  }

  /** Resolves the configured dialect, auto-detecting when set to {@code "auto"}. */
  public DatabaseDialect resolveDialect(DataSource dataSource) {
    if ("auto".equalsIgnoreCase(dialect)) {
      return DialectDetector.detect(dataSource);
    }
    return DatabaseDialect.valueOf(dialect.toUpperCase());
  }

  /** Builds a HikariCP {@link DataSource} from the datasource config. */
  public DataSource createDataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(datasource.url());
    if (datasource.username() != null) {
      config.setUsername(datasource.username());
    }
    if (datasource.password() != null) {
      config.setPassword(datasource.password());
    }
    if (datasource.driverClassName() != null && !datasource.driverClassName().isBlank()) {
      config.setDriverClassName(datasource.driverClassName());
    }
    config.setMaximumPoolSize(datasource.maximumPoolSize());
    config.setConnectionTimeout(datasource.connectionTimeoutMs());
    config.setIdleTimeout(datasource.idleTimeoutMs());
    config.setMaxLifetime(datasource.maxLifetimeMs());

    String url = datasource.url();
    if (url != null && url.contains("sqlite")) {
      config.setConnectionInitSql("PRAGMA foreign_keys = ON");
      config.setMaximumPoolSize(1);
    }
    return new HikariDataSource(config);
  }

  /** Builds a {@link LockManager} from the lock config and the given {@link DataSource}. */
  public LockManager createLockManager(DataSource dataSource) {
    return LockManagerFactory.create(lock, dataSource, null);
  }

  /** Builder. */
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private DataSourceConfig datasource;
    private LockConfig lock = LockConfig.local();
    private String dialect = "auto";

    public Builder datasource(DataSourceConfig datasource) {
      this.datasource = datasource;
      return this;
    }

    public Builder lock(LockConfig lock) {
      this.lock = lock;
      return this;
    }

    public Builder dialect(String dialect) {
      this.dialect = dialect;
      return this;
    }

    public DatabaseConfig build() {
      if (datasource == null) {
        throw new IllegalStateException("datasource must be set");
      }
      return new DatabaseConfig(this);
    }
  }
}
