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
import com.google.adk.lock.LockType;
import org.jspecify.annotations.Nullable;

/** Configuration for {@link LockManager}. */
public record LockConfig(LockType type, long lockTimeoutMs, @Nullable RedissonConfig redis) {

  public LockConfig {
    type = type == null ? LockType.Local : type;
  }

  /** Default config: in-process locking with a 10s timeout. */
  public static LockConfig local() {
    return new LockConfig(LockType.Local, 10_000L, null);
  }

  /** Default config: in-process locking with a custom timeout. */
  public static LockConfig local(long lockTimeoutMs) {
    return new LockConfig(LockType.Local, lockTimeoutMs, null);
  }

  /** Redis config. */
  public static LockConfig redis(RedissonConfig redis) {
    return new LockConfig(LockType.REDIS, 10_000L, redis);
  }

  /** Database config with a 10s acquisition timeout. */
  public static LockConfig database() {
    return new LockConfig(LockType.DATABASE, 10_000L, null);
  }
}
