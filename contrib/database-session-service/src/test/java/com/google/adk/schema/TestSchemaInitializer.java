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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import javax.sql.DataSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Test utility that applies the ADK schema SQL scripts for a given dialect.
 *
 * <p>Reads all {@code V*.sql} files from {@code classpath:db/migration/<dialect>/} in version order
 * and executes them via Spring's {@link ScriptUtils}. This is intended for test setup only -
 * production code does not auto-initialize the schema.
 *
 * <p>Initialization is idempotent: if the {@code adk_sessions} table already exists, the scripts
 * are skipped. This allows tests to reuse a persistent database (e.g. a local MySQL instance)
 * across runs without hitting "table already exists" errors.
 */
public final class TestSchemaInitializer {

  private TestSchemaInitializer() {}

  /** Runs all {@code V*.sql} scripts for the given dialect in version order, if not already done. */
  public static void initialize(DataSource dataSource, DatabaseDialect dialect) {
    String pattern = "classpath:db/migration/" + dialect.name().toLowerCase() + "/V*.sql";
    ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    try {
      Resource[] resources = resolver.getResources(pattern);
      Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
      try (Connection connection = dataSource.getConnection()) {
        if (tableExists(connection, "adk_sessions")) {
          return;
        }
        for (Resource resource : resources) {
          ScriptUtils.executeSqlScript(connection, resource);
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to initialize schema for dialect " + dialect, e);
    }
  }

  private static boolean tableExists(Connection connection, String tableName) throws SQLException {
    try (ResultSet tables =
        connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
      while (tables.next()) {
        String name = tables.getString("TABLE_NAME");
        if (name != null && name.equalsIgnoreCase(tableName)) {
          return true;
        }
      }
      return false;
    }
  }
}
