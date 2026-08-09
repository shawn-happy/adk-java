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

import com.google.adk.sessions.SessionException;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * Redis-based {@link LockManager} backed by Redisson.
 *
 * <p>Supports watchdog auto-renewal when {@code leaseTimeoutMs < 0}.
 */
public final class RedissonLockManager implements LockManager {

  private static final String LOCK_KEY_PREFIX = "adk:session:lock:";

  private final RedissonClient redissonClient;
  private final long waitTimeoutMs;
  private final long leaseTimeoutMs;

  public RedissonLockManager(RedissonClient redissonClient) {
    this(redissonClient, 10_000L, -1L);
  }

  public RedissonLockManager(
      RedissonClient redissonClient, long waitTimeoutMs, long leaseTimeoutMs) {
    this.redissonClient = redissonClient;
    this.waitTimeoutMs = waitTimeoutMs;
    this.leaseTimeoutMs = leaseTimeoutMs;
  }

  @Override
  public AdkSessionLock acquireLock(String appName, String userId, String sessionId) {
    String lockKey = buildLockKey(appName, userId, sessionId);
    RLock lock = redissonClient.getLock(lockKey);
    try {
      boolean acquired = lock.tryLock(waitTimeoutMs, leaseTimeoutMs, TimeUnit.MILLISECONDS);
      if (!acquired) {
        throw new SessionException("Failed to acquire Redis lock for session: " + lockKey);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SessionException("Interrupted while acquiring lock: " + lockKey, e);
    }
    return new RedissonAdkSessionLock(lock);
  }

  @Override
  public void releaseLock(AdkSessionLock token) {
    token.release();
  }

  private static String buildLockKey(String appName, String userId, String sessionId) {
    return LOCK_KEY_PREFIX + appName + ":" + userId + ":" + sessionId;
  }

  private static final class RedissonAdkSessionLock implements AdkSessionLock {
    private final RLock lock;

    RedissonAdkSessionLock(RLock lock) {
      this.lock = lock;
    }

    @Override
    public void release() {
      if (lock != null && lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }
}
