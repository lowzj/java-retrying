/*
 * Copyright 2012-2015 Ray Holder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.lowzj.retry;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.lowzj.retry.Retryer.RetryerCallable;
import com.github.lowzj.retry.attempt.Attempt;
import org.junit.Test;

import static com.github.lowzj.retry.TestCallable.alwaysNull;
import static com.github.lowzj.retry.TestCallable.noIOExceptionAfter5Attempts;
import static com.github.lowzj.retry.TestCallable.noIllegalStateExceptionAfter5Attempts;
import static com.github.lowzj.retry.TestCallable.notNullAfter5Attempts;
import static com.github.lowzj.retry.TestCallable.notNullResultOrIOExceptionOrRuntimeExceptionAfter5Attempts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RetryerBuilderTest {

    @Test
    public void testWithWaitStrategy() throws ExecutionException, RetryException {
        Callable<Boolean> callable = notNullAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .withWaitStrategy(WaitStrategies.fixedWait(50L, TimeUnit.MILLISECONDS))
                .retryIfResult(Objects::isNull)
                .build();
        long start = System.currentTimeMillis();
        boolean result = retryer.call(callable);
        assertTrue(System.currentTimeMillis() - start >= 250L);
        assertTrue(result);
    }

    @Test
    public void testWithMoreThanOneWaitStrategyOneBeingFixed() throws ExecutionException, RetryException {
        Callable<Boolean> callable = notNullAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .withWaitStrategy(WaitStrategies.join(
                        WaitStrategies.fixedWait(50L, TimeUnit.MILLISECONDS),
                        WaitStrategies.fibonacciWait(10, Long.MAX_VALUE, TimeUnit.MILLISECONDS)))
                .retryIfResult(Objects::isNull)
                .build();
        long start = System.currentTimeMillis();
        boolean result = retryer.call(callable);
        assertTrue(System.currentTimeMillis() - start >= 370L);
        assertTrue(result);
    }

    @Test
    public void testWithMoreThanOneWaitStrategyOneBeingIncremental() throws ExecutionException, RetryException {
        Callable<Boolean> callable = notNullAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .withWaitStrategy(WaitStrategies.join(
                        WaitStrategies.incrementingWait(10L, TimeUnit.MILLISECONDS, 10L, TimeUnit.MILLISECONDS),
                        WaitStrategies.fibonacciWait(10, Long.MAX_VALUE, TimeUnit.MILLISECONDS)))
                .retryIfResult(Objects::isNull)
                .build();
        long start = System.currentTimeMillis();
        boolean result = retryer.call(callable);
        assertTrue(System.currentTimeMillis() - start >= 270L);
        assertTrue(result);
    }

    @Test
    public void testWithStopStrategy() throws ExecutionException {
        Callable<Boolean> callable = notNullAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .retryIfResult(Objects::isNull)
                .build();
        try {
            retryer.call(callable);
            fail("RetryException expected");
        } catch (RetryException e) {
            assertEquals(3, e.getNumberOfFailedAttempts());
        }
    }

    @Test
    public void testWithBlockStrategy() throws ExecutionException, RetryException {
        Callable<Boolean> callable = notNullAfter5Attempts();
        final AtomicInteger counter = new AtomicInteger();
        BlockStrategy blockStrategy = new BlockStrategy() {
            @Override
            public void block(long sleepTime) throws InterruptedException {
                counter.incrementAndGet();
            }
        };

        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .withBlockStrategy(blockStrategy)
                .retryIfResult(Objects::isNull)
                .build();
        final int retryCount = 5;
        boolean result = retryer.call(callable);
        assertTrue(result);
        assertEquals(counter.get(), retryCount);
    }

    @Test
    public void testRetryIfException() throws ExecutionException, RetryException {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfException()
                .build();
        boolean result = retryer.call(callable);
        assertTrue(result);

        callable = noIOExceptionAfter5Attempts();
        retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        try {
            retryer.call(callable);
            fail("RetryException expected");
        } catch (RetryException e) {
            assertEquals(3, e.getNumberOfFailedAttempts());
            assertTrue(e.getLastFailedAttempt().hasException());
            assertTrue(e.getLastFailedAttempt().getExceptionCause() instanceof IOException);
            assertTrue(e.getCause() instanceof IOException);
        }

        callable = noIllegalStateExceptionAfter5Attempts();
        retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        try {
            retryer.call(callable);
            fail("RetryException expected");
        } catch (RetryException e) {
            assertEquals(3, e.getNumberOfFailedAttempts());
            assertTrue(e.getLastFailedAttempt().hasException());
            assertTrue(e.getLastFailedAttempt().getExceptionCause() instanceof IllegalStateException);
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void testRetryIfRuntimeException() throws ExecutionException, RetryException {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfRuntimeException()
                .build();
        try {
            retryer.call(callable);
            fail("ExecutionException expected");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IOException);
        }

        callable = noIllegalStateExceptionAfter5Attempts();
        assertTrue(retryer.call(callable));

        callable = noIllegalStateExceptionAfter5Attempts();
        retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfRuntimeException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        try {
            retryer.call(callable);
            fail("RetryException expected");
        } catch (RetryException e) {
            assertEquals(3, e.getNumberOfFailedAttempts());
            assertTrue(e.getLastFailedAttempt().hasException());
            assertTrue(e.getLastFailedAttempt().getExceptionCause() instanceof IllegalStateException);
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void testRetryIfExceptionOfType() throws RetryException, ExecutionException {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .build();
        assertTrue(retryer.call(callable));

        callable = noIllegalStateExceptionAfter5Attempts();
        try {
            retryer.call(callable);
            fail("ExecutionException expected");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }

        callable = noIOExceptionAfter5Attempts();
        retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        try {
            retryer.call(callable);
            fail("RetryException expected");
        } catch (RetryException e) {
            assertEquals(3, e.getNumberOfFailedAttempts());
            assertTrue(e.getLastFailedAttempt().hasException());
            assertTrue(e.getLastFailedAttempt().getExceptionCause() instanceof IOException);
            assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    public void testRetryIfExceptionWithPredicate() throws RetryException, ExecutionException {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfException(t -> t instanceof IOException)
                .build();
        assertTrue(retryer.call(callable));

        callable = noIllegalStateExceptionAfter5Attempts();
        try {
            retryer.call(callable);
            fail("ExecutionException expected");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }

        callable = noIOExceptionAfter5Attempts();
        retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfException(t -> t instanceof IOException)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        try {
            retryer.call(callable);
            fail("RetryException expected");
        } catch (RetryException e) {
            assertEquals(3, e.getNumberOfFailedAttempts());
            assertTrue(e.getLastFailedAttempt().hasException());
            assertTrue(e.getLastFailedAttempt().getExceptionCause() instanceof IOException);
            assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    public void testRetryIfResult() throws ExecutionException, RetryException {
        Callable<Boolean> callable = notNullAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfResult(Objects::isNull)
                .build();
        assertTrue(retryer.call(callable));

        callable = notNullAfter5Attempts();
        retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfResult(Objects::isNull)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        try {
            retryer.call(callable);
            fail("RetryException expected");
        } catch (RetryException e) {
            assertEquals(3, e.getNumberOfFailedAttempts());
            assertTrue(e.getLastFailedAttempt().hasResult());
            assertNull(e.getLastFailedAttempt().getResult());
            assertNull(e.getCause());
        }
    }

    @Test
    public void testMultipleRetryConditions() throws ExecutionException, RetryException {
        Callable<Boolean> callable = notNullResultOrIOExceptionOrRuntimeExceptionAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfResult(Objects::isNull)
                .retryIfExceptionOfType(IOException.class)
                .retryIfRuntimeException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        try {
            retryer.call(callable);
            fail("RetryException expected");
        } catch (RetryException e) {
            assertTrue(e.getLastFailedAttempt().hasException());
            assertTrue(e.getLastFailedAttempt().getExceptionCause() instanceof IllegalStateException);
            assertTrue(e.getCause() instanceof IllegalStateException);
        }

        callable = notNullResultOrIOExceptionOrRuntimeExceptionAfter5Attempts();
        retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfResult(Objects::isNull)
                .retryIfExceptionOfType(IOException.class)
                .retryIfRuntimeException()
                .build();
        assertTrue(retryer.call(callable));
    }

    @Test
    public void testInterruption() throws InterruptedException, ExecutionException {
        final AtomicBoolean result = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                        .withWaitStrategy(WaitStrategies.fixedWait(1000L, TimeUnit.MILLISECONDS))
                        .retryIfResult(Objects::isNull)
                        .build();
                try {
                    retryer.call(alwaysNull(latch));
                    fail("RetryException expected");
                } catch (RetryException e) {
                    assertTrue(!e.getLastFailedAttempt().hasException());
                    assertNull(e.getCause());
                    assertTrue(Thread.currentThread().isInterrupted());
                    result.set(true);
                } catch (ExecutionException e) {
                    fail("RetryException expected");
                }
            }
        };
        Thread t = new Thread(r);
        t.start();
        latch.countDown();
        t.interrupt();
        t.join();
        assertTrue(result.get());
    }

    @Test
    public void testWrap() throws ExecutionException, RetryException {
        Callable<Boolean> callable = notNullAfter5Attempts();
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfResult(Objects::isNull)
                .build();
        RetryerCallable<Boolean> wrapped = retryer.wrap(callable);
        assertTrue(wrapped.call());
    }

    @Test
    public void testWhetherBuilderFailsForNullStopStrategy() {
        try {
            RetryerBuilder.<Void>newBuilder()
                    .withStopStrategy(null)
                    .build();
            fail("Exepcted to fail for null stop strategy");
        } catch (NullPointerException exception) {
            assertTrue(exception.getMessage().contains("stopStrategy may not be null"));
        }
    }

    @Test
    public void testWhetherBuilderFailsForNullWaitStrategy() {
        try {
            RetryerBuilder.<Void>newBuilder()
                    .withWaitStrategy(null)
                    .build();
            fail("Exepcted to fail for null wait strategy");
        } catch (NullPointerException exception) {
            assertTrue(exception.getMessage().contains("waitStrategy may not be null"));
        }
    }

    @Test
    public void testWhetherBuilderFailsForNullWaitStrategyWithCompositeStrategies() {
        try {
            RetryerBuilder.<Void>newBuilder()
                    .withWaitStrategy(WaitStrategies.join(null, null))
                    .build();
            fail("Exepcted to fail for null wait strategy");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("Cannot have a null wait strategy"));
        }
    }

    @Test
    public void testRetryListener_SuccessfulAttempt() throws Exception {
        final Map<Long, Attempt> attempts = new HashMap<Long, Attempt>();

        Callable<Boolean> callable = notNullAfter5Attempts();

        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfResult(Objects::isNull)
                .withRetryListener(attempt -> attempts.put(attempt.getAttemptNumber(), attempt))
                .build();
        assertTrue(retryer.call(callable));

        assertEquals(6, attempts.size());

        assertResultAttempt(attempts.get(1L), true, null);
        assertResultAttempt(attempts.get(2L), true, null);
        assertResultAttempt(attempts.get(3L), true, null);
        assertResultAttempt(attempts.get(4L), true, null);
        assertResultAttempt(attempts.get(5L), true, null);
        assertResultAttempt(attempts.get(6L), true, true);
    }

    @Test
    public void testRetryListener_WithException() throws Exception {
        final Map<Long, Attempt> attempts = new HashMap<Long, Attempt>();

        Callable<Boolean> callable = noIOExceptionAfter5Attempts();

        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfResult(Objects::isNull)
                .retryIfException()
                .withRetryListener(attempt -> attempts.put(attempt.getAttemptNumber(), attempt))
                .build();
        assertTrue(retryer.call(callable));

        assertEquals(6, attempts.size());

        assertExceptionAttempt(attempts.get(1L), true, IOException.class);
        assertExceptionAttempt(attempts.get(2L), true, IOException.class);
        assertExceptionAttempt(attempts.get(3L), true, IOException.class);
        assertExceptionAttempt(attempts.get(4L), true, IOException.class);
        assertExceptionAttempt(attempts.get(5L), true, IOException.class);
        assertResultAttempt(attempts.get(6L), true, true);
    }

    @Test
    public void testMultipleRetryListeners() throws Exception {
        Callable<Boolean> callable = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return true;
            }
        };

        final AtomicBoolean listenerOne = new AtomicBoolean(false);
        final AtomicBoolean listenerTwo = new AtomicBoolean(false);

        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .withRetryListener(attempt -> listenerOne.set(true))
                .withRetryListener(attempt -> listenerTwo.set(true))
                .build();

        assertTrue(retryer.call(callable));
        assertTrue(listenerOne.get());
        assertTrue(listenerTwo.get());
    }

    private void assertResultAttempt(Attempt actualAttempt, boolean expectedHasResult, Object expectedResult) {
        assertFalse(actualAttempt.hasException());
        assertEquals(expectedHasResult, actualAttempt.hasResult());
        assertEquals(expectedResult, actualAttempt.getResult());
    }

    private void assertExceptionAttempt(Attempt actualAttempt, boolean expectedHasException, Class<?> expectedExceptionClass) {
        assertFalse(actualAttempt.hasResult());
        assertEquals(expectedHasException, actualAttempt.hasException());
        assertTrue(expectedExceptionClass.isInstance(actualAttempt.getExceptionCause()));
    }
}
