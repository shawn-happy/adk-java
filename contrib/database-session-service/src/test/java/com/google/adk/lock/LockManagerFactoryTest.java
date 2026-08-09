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

package com.google.adk.lock;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.google.adk.config.LockConfig;
import com.google.adk.config.RedissonConfig;
import com.google.adk.sessions.SessionException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

/** Unit tests for {@link LockManagerFactory}. */
public class LockManagerFactoryTest {

  /** Local config creates an in-process lock manager. */
  @Test
  void create_localConfig_returnsLocalLockManager() {
    LockManager manager = LockManagerFactory.create(LockConfig.local());

    assertThat(manager).isInstanceOf(LocalLockManager.class);
  }

  /** Database config creates a database lock manager backed by the given DataSource. */
  @Test
  void create_databaseConfig_returnsDatabaseLockManager() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:lock_manager_factory_test;DB_CLOSE_DELAY=-1");

    LockManager manager = LockManagerFactory.create(LockConfig.database(), dataSource, null);

    assertThat(manager).isInstanceOf(DatabaseLockManager.class);
  }

  /** Database config without a DataSource fails with SessionException. */
  @Test
  void create_databaseConfigWithoutDataSource_throwsSessionException() {
    assertThrows(
        SessionException.class, () -> LockManagerFactory.create(LockConfig.database(), null, null));
  }

  /** Redis config with a pre-built client wraps that client without connecting. */
  @Test
  void create_redisConfigWithClient_returnsRedissonLockManager() {
    RedissonClient client = mock(RedissonClient.class);
    LockConfig config =
        LockConfig.redis(new RedissonConfig("redis://localhost:6379", 0, null, 5_000L, 3_000L));

    LockManager manager = LockManagerFactory.create(config, null, client);

    assertThat(manager).isInstanceOf(RedissonLockManager.class);
  }

  /** Redis config without a RedissonConfig and without a client fails with SessionException. */
  @Test
  void create_redisConfigWithoutRedisSettings_throwsSessionException() {
    LockConfig config = new LockConfig(LockType.REDIS, 10_000L, null);

    assertThrows(SessionException.class, () -> LockManagerFactory.create(config, null, null));
  }
}
