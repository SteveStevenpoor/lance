/*
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
package org.lance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestUtilsTest {
  private ExecutorService executor;

  @AfterEach
  void tearDown() {
    if (executor != null) {
      TestUtils.shutdownExecutor(executor);
    }
  }

  @Test
  void boundedFixedThreadPoolRejectsTasksBeyondWorkerCapacity() throws Exception {
    executor = TestUtils.newBoundedFixedThreadPool(1);
    CountDownLatch taskStarted = new CountDownLatch(1);
    CountDownLatch releaseTask = new CountDownLatch(1);
    executor.submit(
        () -> {
          taskStarted.countDown();
          releaseTask.await();
          return null;
        });

    assertTrue(taskStarted.await(5, TimeUnit.SECONDS));
    assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> null));
    releaseTask.countDown();
  }

  @Test
  void shutdownExecutorsStopsRemainingExecutorsAfterFailure() {
    ExecutorService failingExecutor =
        new java.util.concurrent.AbstractExecutorService() {
          @Override
          public void shutdown() {}

          @Override
          public java.util.List<Runnable> shutdownNow() {
            throw new IllegalStateException("shutdown failed");
          }

          @Override
          public boolean isShutdown() {
            return false;
          }

          @Override
          public boolean isTerminated() {
            return true;
          }

          @Override
          public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
          }

          @Override
          public void execute(Runnable command) {}
        };
    executor = TestUtils.newBoundedFixedThreadPool(1);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> TestUtils.shutdownExecutors(Arrays.asList(failingExecutor, executor)));

    assertTrue(executor.isShutdown());
    assertEquals(1, exception.getSuppressed().length);
  }

  @Test
  void taskResultAssertionRetainsFirstTaskFailure() {
    Exception taskFailure = new Exception("task failed");

    AssertionError exception =
        assertThrows(AssertionError.class, () -> TestUtils.assertTaskResults(1, 0, 1, taskFailure));

    assertEquals(taskFailure, exception.getCause());
  }
}
