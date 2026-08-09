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

import com.google.adk.lock.LockManager.AdkSessionLock;
import com.google.adk.schema.DatabaseDialect;
import com.google.adk.schema.TestSchemaInitializer;
import com.google.adk.sessions.SessionException;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DatabaseLockManager} against an in-memory H2 database.
 *
 * <p>Each test uses a unique session id so the shared lock table never leaks state between tests.
 */
public class DatabaseLockManagerTest {

  private static JdbcDataSource dataSource;

  @BeforeAll
  static void setUp() {
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:database_lock_manager_test;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    TestSchemaInitializer.initialize(dataSource, DatabaseDialect.H2);
  }

  private static String newSessionId() {
    return "session-" + UUID.randomUUID();
  }

  /** Acquiring and releasing a database lock completes successfully. */
  @Test
  void acquireLock_thenRelease_completesSuccessfully() {
    DatabaseLockManager manager = new DatabaseLockManager(dataSource);

    AdkSessionLock lock = manager.acquireLock("app", "user", newSessionId());

    assertThat(lock).isNotNull();
    manager.releaseLock(lock);
  }

  /** While one manager holds the lock, another manager times out with SessionException. */
  @Test
  void acquireLock_heldByAnotherManager_timesOutAndThrows() {
    String sessionId = newSessionId();
    DatabaseLockManager holder = new DatabaseLockManager(dataSource);
    AdkSessionLock lock = holder.acquireLock("app", "user", sessionId);
    try {
      DatabaseLockManager contender = new DatabaseLockManager(dataSource, 200L);

      assertThrows(SessionException.class, () -> contender.acquireLock("app", "user", sessionId));
    } finally {
      holder.releaseLock(lock);
    }
  }

  /** After a lock is released, another manager can acquire it immediately. */
  @Test
  void acquireLock_afterRelease_otherManagerAcquires() {
    String sessionId = newSessionId();
    DatabaseLockManager first = new DatabaseLockManager(dataSource);
    AdkSessionLock lock = first.acquireLock("app", "user", sessionId);
    first.releaseLock(lock);

    DatabaseLockManager second = new DatabaseLockManager(dataSource);
    AdkSessionLock reacquired = second.acquireLock("app", "user", sessionId);

    assertThat(reacquired).isNotNull();
    second.releaseLock(reacquired);
  }

  /** An expired lock left by a crashed holder is cleaned up and taken over. */
  @Test
  void acquireLock_expiredLock_isTakenOver() {
    String sessionId = newSessionId();
    // Tiny TTL, not released: simulates a crashed lock holder.
    DatabaseLockManager crashedHolder = new DatabaseLockManager(dataSource, 10_000L, 100L, 50L);
    crashedHolder.acquireLock("app", "user", sessionId);

    DatabaseLockManager takeover = new DatabaseLockManager(dataSource, 5_000L, 30_000L, 50L);

    AdkSessionLock lock = takeover.acquireLock("app", "user", sessionId);
    assertThat(lock).isNotNull();
    takeover.releaseLock(lock);
  }

  /** Locks of different sessions are independent and do not block each other. */
  @Test
  void acquireLock_differentSessionKeys_doNotBlock() {
    DatabaseLockManager manager = new DatabaseLockManager(dataSource);
    AdkSessionLock lock = manager.acquireLock("app", "user", newSessionId());
    try {
      AdkSessionLock other = manager.acquireLock("app", "user", newSessionId());

      assertThat(other).isNotNull();
      manager.releaseLock(other);
    } finally {
      manager.releaseLock(lock);
    }
  }
}
