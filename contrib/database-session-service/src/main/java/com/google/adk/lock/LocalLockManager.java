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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-process {@link LockManager} using {@link ReentrantLock} with reference-counted cleanup.
 *
 * <p>Mirrors Python ADK's {@code _with_session_lock}: per-session lock granularity, guard-protected
 * get-or-create, reference-counted cleanup, {@code isLocked()} check before removal.
 */
public final class LocalLockManager implements LockManager {

  /** Keyed by (appName, userId, sessionId). */
  record SessionLockKey(String appName, String userId, String sessionId) {}

  private final ConcurrentHashMap<SessionLockKey, ReentrantLock> sessionLocks =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<SessionLockKey, Integer> refCounts = new ConcurrentHashMap<>();
  private final ReentrantLock guard = new ReentrantLock();
  private final long lockTimeoutMs;

  public LocalLockManager() {
    this(10_000L);
  }

  public LocalLockManager(long lockTimeoutMs) {
    this.lockTimeoutMs = lockTimeoutMs;
  }

  @Override
  public AdkSessionLock acquireLock(String appName, String userId, String sessionId) {
    SessionLockKey lockKey = new SessionLockKey(appName, userId, sessionId);
    ReentrantLock lock;
    guard.lock();
    try {
      lock = sessionLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
      refCounts.merge(lockKey, 1, Integer::sum);
    } finally {
      guard.unlock();
    }

    try {
      boolean acquired = lock.tryLock(lockTimeoutMs, TimeUnit.MILLISECONDS);
      if (!acquired) {
        decrementRefCount(lockKey, lock);
        throw new SessionException(
            "Failed to acquire in-process lock for session: "
                + lockKey
                + " within "
                + lockTimeoutMs
                + "ms");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      decrementRefCount(lockKey, lock);
      throw new SessionException("Interrupted while acquiring lock: " + lockKey, e);
    }
    return new LocalAdkSessionLock(this, lockKey, lock);
  }

  @Override
  public void releaseLock(AdkSessionLock token) {
    if (token instanceof LocalAdkSessionLock inProcess) {
      inProcess.release();
    }
  }

  private void decrementRefCount(SessionLockKey lockKey, ReentrantLock lock) {
    guard.lock();
    try {
      int remaining = refCounts.merge(lockKey, -1, Integer::sum);
      if (remaining <= 0 && !lock.isLocked()) {
        sessionLocks.remove(lockKey);
        refCounts.remove(lockKey);
      }
    } finally {
      guard.unlock();
    }
  }

  private static final class LocalAdkSessionLock implements AdkSessionLock {
    private final LocalLockManager manager;
    private final SessionLockKey lockKey;
    private final ReentrantLock lock;

    LocalAdkSessionLock(LocalLockManager manager, SessionLockKey lockKey, ReentrantLock lock) {
      this.manager = manager;
      this.lockKey = lockKey;
      this.lock = lock;
    }

    @Override
    public void release() {
      try {
        lock.unlock();
      } finally {
        manager.decrementRefCount(lockKey, lock);
      }
    }
  }
}
