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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.adk.lock.LockManager.AdkSessionLock;
import com.google.adk.sessions.SessionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/** Unit tests for {@link RedissonLockManager} with a mocked {@link RedissonClient}. */
public class RedissonLockManagerTest {

  /** Acquisition requests the namespaced lock key with the configured timeouts. */
  @Test
  void acquireLock_acquired_usesExpectedKeyAndTimeouts() throws Exception {
    RedissonClient client = mock(RedissonClient.class);
    RLock lock = mock(RLock.class);
    when(client.getLock("adk:session:lock:app:user:session")).thenReturn(lock);
    when(lock.tryLock(5_000L, 3_000L, TimeUnit.MILLISECONDS)).thenReturn(true);
    RedissonLockManager manager = new RedissonLockManager(client, 5_000L, 3_000L);

    AdkSessionLock token = manager.acquireLock("app", "user", "session");

    assertThat(token).isNotNull();
    verify(client).getLock("adk:session:lock:app:user:session");
    verify(lock).tryLock(5_000L, 3_000L, TimeUnit.MILLISECONDS);
  }

  /** The default constructor applies a 10s wait timeout and watchdog lease (-1). */
  @Test
  void acquireLock_defaultConstructor_usesDefaultTimeouts() throws Exception {
    RedissonClient client = mock(RedissonClient.class);
    RLock lock = mock(RLock.class);
    when(client.getLock(any(String.class))).thenReturn(lock);
    when(lock.tryLock(10_000L, -1L, TimeUnit.MILLISECONDS)).thenReturn(true);
    RedissonLockManager manager = new RedissonLockManager(client);

    AdkSessionLock token = manager.acquireLock("app", "user", "session");

    assertThat(token).isNotNull();
    verify(lock).tryLock(10_000L, -1L, TimeUnit.MILLISECONDS);
  }

  /** A lock that cannot be acquired within the wait timeout fails with SessionException. */
  @Test
  void acquireLock_notAcquired_throwsSessionException() throws Exception {
    RedissonClient client = mock(RedissonClient.class);
    RLock lock = mock(RLock.class);
    when(client.getLock(any(String.class))).thenReturn(lock);
    when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
    RedissonLockManager manager = new RedissonLockManager(client);

    assertThrows(SessionException.class, () -> manager.acquireLock("app", "user", "session"));
  }

  /** Interrupting acquisition fails with SessionException and restores the interrupt flag. */
  @Test
  void acquireLock_interrupted_throwsSessionExceptionAndRestoresInterruptFlag() throws Exception {
    RedissonClient client = mock(RedissonClient.class);
    RLock lock = mock(RLock.class);
    when(client.getLock(any(String.class))).thenReturn(lock);
    when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
        .thenThrow(new InterruptedException("interrupted"));
    RedissonLockManager manager = new RedissonLockManager(client);

    assertThrows(SessionException.class, () -> manager.acquireLock("app", "user", "session"));

    assertThat(Thread.interrupted()).isTrue();
  }

  /** Releasing a token held by the current thread unlocks the underlying lock. */
  @Test
  void releaseLock_heldByCurrentThread_unlocks() throws Exception {
    RedissonClient client = mock(RedissonClient.class);
    RLock lock = mock(RLock.class);
    when(client.getLock(any(String.class))).thenReturn(lock);
    when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    when(lock.isHeldByCurrentThread()).thenReturn(true);
    RedissonLockManager manager = new RedissonLockManager(client);
    AdkSessionLock token = manager.acquireLock("app", "user", "session");

    manager.releaseLock(token);

    verify(lock).unlock();
  }

  /** Releasing a token not held by the current thread leaves the underlying lock untouched. */
  @Test
  void releaseLock_notHeldByCurrentThread_doesNotUnlock() throws Exception {
    RedissonClient client = mock(RedissonClient.class);
    RLock lock = mock(RLock.class);
    when(client.getLock(any(String.class))).thenReturn(lock);
    when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    when(lock.isHeldByCurrentThread()).thenReturn(false);
    RedissonLockManager manager = new RedissonLockManager(client);
    AdkSessionLock token = manager.acquireLock("app", "user", "session");

    manager.releaseLock(token);

    verify(lock, never()).unlock();
  }
}
