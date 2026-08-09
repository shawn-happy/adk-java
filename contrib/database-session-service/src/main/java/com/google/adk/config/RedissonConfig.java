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
import org.jspecify.annotations.Nullable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/** Configuration for the Redisson-based {@link LockManager}. */
public record RedissonConfig(
    String address,
    int database,
    @Nullable String password,
    long waitTimeoutMs,
    long leaseTimeoutMs) {

  public RedissonConfig {
    if (address == null || address.isBlank()) {
      throw new IllegalArgumentException("Redisson address must not be null or blank");
    }
  }

  /** Builds a {@link RedissonClient }. */
  public RedissonClient createClient() {
    Config config = new Config();
    config.useSingleServer().setAddress(address).setDatabase(database);
    if (password != null && !password.isBlank()) {
      config.useSingleServer().setPassword(password);
    }
    return Redisson.create(config);
  }
}
