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

/** Acquires and releases per-session locks. Used to serialize {@code appendEvent}. */
public interface LockManager {

  /**
   * Acquires a session-scoped lock, blocking until acquired or timed out.
   *
   * @param appName application name
   * @param userId user id
   * @param sessionId session id
   * @return a {@link AdkSessionLock} that must be released in a {@code finally} block
   */
  AdkSessionLock acquireLock(String appName, String userId, String sessionId);

  /** Releases the given token. Safe to call in a {@code finally} block. */
  void releaseLock(AdkSessionLock token);

  /** Opaque handle to an acquired lock. Call {@link #release()} to release it. */
  public static interface AdkSessionLock {

    /** Releases the underlying lock. */
    void release();
  }
}
