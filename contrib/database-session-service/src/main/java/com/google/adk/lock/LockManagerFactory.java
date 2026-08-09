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

import com.google.adk.config.LockConfig;
import com.google.adk.config.RedissonConfig;
import com.google.adk.sessions.SessionException;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.redisson.api.RedissonClient;

/** Creates {@link LockManager} instances from {@link LockConfig}. */
public final class LockManagerFactory {

  private LockManagerFactory() {}

  /**
   * Builds a {@link LockManager}. For {@link LockType#REDIS}, pass a pre-built {@link
   * RedissonClient} (or {@code null} to construct one from {@link RedissonConfig}). For {@link
   * LockType#DATABASE}, pass the {@link DataSource} that backs the {@code adk_session_lock} table.
   */
  public static LockManager create(
      LockConfig config, @Nullable DataSource dataSource, @Nullable RedissonClient redissonClient) {
    return switch (config.type()) {
      case REDIS -> {
        if (redissonClient != null) {
          yield new RedissonLockManager(
              redissonClient,
              config.redis() == null ? 10_000L : config.redis().waitTimeoutMs(),
              config.redis() == null ? -1L : config.redis().leaseTimeoutMs());
        }
        if (config.redis() == null) {
          throw new SessionException("LockConfig for REDIS requires a RedissonConfig");
        }
        RedissonConfig rc = config.redis();
        yield new RedissonLockManager(rc.createClient(), rc.waitTimeoutMs(), rc.leaseTimeoutMs());
      }
      case DATABASE -> {
        if (dataSource == null) {
          throw new SessionException("LockConfig for DATABASE requires a DataSource");
        }
        yield new DatabaseLockManager(dataSource, config.lockTimeoutMs());
      }
      default -> new LocalLockManager(config.lockTimeoutMs());
    };
  }

  /** Convenience overload using {@link LockConfig} only (IN_PROCESS and REDIS only). */
  public static LockManager create(LockConfig config) {
    return create(config, null, null);
  }
}
