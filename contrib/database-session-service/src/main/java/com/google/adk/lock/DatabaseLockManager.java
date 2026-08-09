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
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Database-backed {@link LockManager} using a dedicated {@code adk_session_lock} table.
 *
 * <p>This is a generic, cross-database distributed lock that works across MySQL, PostgreSQL,
 * Oracle, SQLite, and H2. The {@code adk_session_lock} table has a unique {@code lock_key} column
 * (the primary key). Lock acquisition is an {@code INSERT}; if the row already exists, Spring
 * translates the constraint violation to {@link DuplicateKeyException} and the manager retries
 * after a short sleep.
 *
 * <p>Each lock row stores an {@code owner_id} (a UUID generated per acquisition) and an {@code
 * expire_at} epoch-millis value. Before each acquisition attempt, expired locks for the target key
 * are deleted, so crashed processes do not permanently block progress. The TTL defaults to 30
 * seconds; the acquisition timeout defaults to 10 seconds.
 *
 * <p>Lock operations use auto-commit (each {@code INSERT}/{@code DELETE} is its own transaction) so
 * that lock state is immediately visible to other connections.
 */
public final class DatabaseLockManager implements LockManager {

  private static final String INSERT_LOCK_SQL =
      "INSERT INTO adk_session_lock (lock_key, owner_id, expire_at) "
          + "VALUES (:lockKey, :ownerId, :expireAt)";

  private static final String DELETE_LOCK_SQL =
      "DELETE FROM adk_session_lock WHERE lock_key = :lockKey AND owner_id = :ownerId";

  private static final String DELETE_STALE_SQL =
      "DELETE FROM adk_session_lock WHERE lock_key = :lockKey AND expire_at < :now";

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final long lockTimeoutMs;
  private final long lockTtlMs;
  private final long retryIntervalMs;

  /** Creates a manager with a 10s acquisition timeout and 30s lock TTL. */
  public DatabaseLockManager(DataSource dataSource) {
    this(dataSource, 10_000L, 30_000L, 100L);
  }

  /** Creates a manager with a custom acquisition timeout, 30s lock TTL, 100ms retry interval. */
  public DatabaseLockManager(DataSource dataSource, long lockTimeoutMs) {
    this(dataSource, lockTimeoutMs, 30_000L, 100L);
  }

  /**
   * Creates a manager with full control over timing parameters.
   *
   * @param lockTimeoutMs max wall-clock time to wait for lock acquisition
   * @param lockTtlMs time-to-live for a held lock (auto-expires if the holder crashes)
   * @param retryIntervalMs sleep duration between acquisition retries
   */
  public DatabaseLockManager(
      DataSource dataSource, long lockTimeoutMs, long lockTtlMs, long retryIntervalMs) {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    this.lockTimeoutMs = lockTimeoutMs;
    this.lockTtlMs = lockTtlMs;
    this.retryIntervalMs = retryIntervalMs;
  }

  @Override
  public AdkSessionLock acquireLock(String appName, String userId, String sessionId) {
    String lockKey = appName + ":" + userId + ":" + sessionId;
    String ownerId = UUID.randomUUID().toString();
    long deadline = System.currentTimeMillis() + lockTimeoutMs;

    while (true) {
      long now = System.currentTimeMillis();
      if (now >= deadline) {
        throw new SessionException(
            "Failed to acquire database lock for: " + lockKey + " within " + lockTimeoutMs + "ms");
      }

      // Clean up expired locks for this key before attempting to acquire.
      SqlParameterSource staleParams =
          new MapSqlParameterSource().addValue("lockKey", lockKey).addValue("now", now);
      jdbcTemplate.update(DELETE_STALE_SQL, staleParams);

      // Attempt to acquire the lock.
      long expireAt = now + lockTtlMs;
      SqlParameterSource insertParams =
          new MapSqlParameterSource()
              .addValue("lockKey", lockKey)
              .addValue("ownerId", ownerId)
              .addValue("expireAt", expireAt);
      try {
        jdbcTemplate.update(INSERT_LOCK_SQL, insertParams);
        return new DatabaseAdkSessionLock(lockKey, ownerId);
      } catch (DuplicateKeyException e) {
        // Lock is held by another owner; wait and retry.
        try {
          Thread.sleep(Math.min(retryIntervalMs, Math.max(1, deadline - now)));
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new SessionException("Interrupted while acquiring lock: " + lockKey, ie);
        }
      }
    }
  }

  @Override
  public void releaseLock(AdkSessionLock token) {
    if (token instanceof DatabaseAdkSessionLock dbToken) {
      dbToken.release();
    }
  }

  private void release(String lockKey, String ownerId) {
    SqlParameterSource params =
        new MapSqlParameterSource().addValue("lockKey", lockKey).addValue("ownerId", ownerId);
    jdbcTemplate.update(DELETE_LOCK_SQL, params);
  }

  private final class DatabaseAdkSessionLock implements AdkSessionLock {
    private final String lockKey;
    private final String ownerId;

    DatabaseAdkSessionLock(String lockKey, String ownerId) {
      this.lockKey = lockKey;
      this.ownerId = ownerId;
    }

    @Override
    public void release() {
      DatabaseLockManager.this.release(lockKey, ownerId);
    }
  }
}
