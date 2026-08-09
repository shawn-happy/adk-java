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
import com.google.adk.sessions.SessionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LocalLockManager}. */
public class LocalLockManagerTest {

  /** Acquiring a released lock again succeeds on the same thread. */
  @Test
  void acquireLock_afterRelease_succeedsAgain() {
    LocalLockManager manager = new LocalLockManager();

    AdkSessionLock lock = manager.acquireLock("app", "user", "session");
    manager.releaseLock(lock);
    AdkSessionLock reacquired = manager.acquireLock("app", "user", "session");

    assertThat(reacquired).isNotNull();
    manager.releaseLock(reacquired);
  }

  /** A lock held by another thread makes acquisition time out with SessionException. */
  @Test
  void acquireLock_heldByAnotherThread_timesOutAndThrows() throws Exception {
    LocalLockManager manager = new LocalLockManager(200);
    AdkSessionLock lock = manager.acquireLock("app", "user", "session");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<AdkSessionLock> future =
          executor.submit(() -> manager.acquireLock("app", "user", "session"));

      ExecutionException e =
          assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));

      assertThat(e).hasCauseThat().isInstanceOf(SessionException.class);
    } finally {
      manager.releaseLock(lock);
      executor.shutdown();
    }
  }

  /** Releasing a lock unblocks a thread waiting for the same session lock. */
  @Test
  void releaseLock_unblocksWaitingThread() throws Exception {
    LocalLockManager manager = new LocalLockManager(10_000);
    AdkSessionLock lock = manager.acquireLock("app", "user", "session");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<AdkSessionLock> future =
          executor.submit(() -> manager.acquireLock("app", "user", "session"));
      // Give the worker time to start blocking on the held lock.
      Thread.sleep(100);

      manager.releaseLock(lock);

      AdkSessionLock acquired = future.get(5, TimeUnit.SECONDS);
      assertThat(acquired).isNotNull();
      // The lock must be released from the thread that acquired it.
      executor.submit(() -> manager.releaseLock(acquired)).get(5, TimeUnit.SECONDS);
    } finally {
      executor.shutdown();
    }
  }

  /** Locks of different sessions are independent and do not block each other. */
  @Test
  void acquireLock_differentSessionKeys_doNotBlock() throws Exception {
    LocalLockManager manager = new LocalLockManager(200);
    AdkSessionLock lock = manager.acquireLock("app", "user", "session-1");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      AdkSessionLock other =
          executor
              .submit(() -> manager.acquireLock("app", "user", "session-2"))
              .get(5, TimeUnit.SECONDS);

      assertThat(other).isNotNull();
      executor.submit(() -> manager.releaseLock(other)).get(5, TimeUnit.SECONDS);
    } finally {
      manager.releaseLock(lock);
      executor.shutdown();
    }
  }

  /** Interrupting a waiting thread fails acquisition and restores the interrupt flag. */
  @Test
  void acquireLock_interruptedWhileWaiting_throwsAndRestoresInterruptFlag() throws Exception {
    LocalLockManager manager = new LocalLockManager(10_000);
    AdkSessionLock lock = manager.acquireLock("app", "user", "session");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> future =
          executor.submit(
              () -> {
                try {
                  manager.acquireLock("app", "user", "session");
                  return false; // Unexpectedly acquired.
                } catch (SessionException expected) {
                  return Thread.currentThread().isInterrupted();
                }
              });
      // Give the worker time to start blocking on the held lock.
      Thread.sleep(100);

      executor.shutdownNow(); // Interrupts the waiting worker.

      assertThat(future.get(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      manager.releaseLock(lock);
    }
  }
}
