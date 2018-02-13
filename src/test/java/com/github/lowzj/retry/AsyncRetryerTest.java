package com.github.lowzj.retry;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.lowzj.retry.attempt.Attempt;
import org.junit.Test;

import static com.github.lowzj.retry.TestCallable.noIllegalStateExceptionAfter5Attempts;
import static com.github.lowzj.retry.TestCallable.notNullAfter5Attempts;
import static com.github.lowzj.retry.TestCallable.notNullAfterAttempts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created on 2018/2/11
 *
 * @author lowzj
 */
public class AsyncRetryerTest {
    private static final String POOL_NAME =  "test-async";
    private ScheduledExecutorService scheduledExecutorService =
        ExecutorsUtil.scheduledExecutorService(POOL_NAME, 1);

    @Test
    public void test() throws Exception {
        Random random = new Random();
        AsyncRetryer<Integer> retryer = RetryerBuilder.<Integer>newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(100L, TimeUnit.MILLISECONDS))
            .retryIfResult(num -> num != 0)
            .buildAsyncRetryer();
        CompletableFuture<Integer> result = retryer.call(()-> random.nextInt(2));

        int num = result.get();
        assertEquals(0, num);
    }

    @Test
    public void testExecutor() throws Exception {
        AsyncRetryer<String> retryer = RetryerBuilder.<String>newBuilder()
            .withExecutor(scheduledExecutorService)
            .retryIfResult(Objects::isNull)
            .buildAsyncRetryer();

        String poolName = retryer.call(() -> Thread.currentThread().getName()).get();
        assertTrue("pool name doesn't start with " + POOL_NAME, poolName.startsWith(POOL_NAME));
    }

    @Test
    public void testAsyncRunning() throws Exception {
        AtomicInteger idx = new AtomicInteger(0);
        AtomicInteger res = new AtomicInteger(0);
        int target = 5;

        AsyncRetryer<Integer> retryer = RetryerBuilder.<Integer>newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(50L, TimeUnit.MILLISECONDS))
            .retryIfResult(i -> i != target)
            .buildAsyncRetryer();

        long start = timeMillis();
        retryer.call(idx::getAndIncrement).whenComplete((r, err) -> res.set(r));

        assertNotEquals(target, res.get());
        sleep(100L);
        assertNotEquals(target, res.get());

        sleep(200L);
        assertEquals(target, res.get());
        assertEquals(target + 1, idx.get());

        long diff = timeMillis() - start;
        assertTrue(diff > 300L && diff < 250L + 300L);
    }

    @Test
    public void testBlockStrategyUselessForAsyncRetryer() throws Exception {
        AtomicBoolean executeBlockStrategy = new AtomicBoolean(false);
        AsyncRetryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(20L, TimeUnit.MILLISECONDS))
            .withBlockStrategy(t -> executeBlockStrategy.set(true))
            .retryIfResult(Objects::isNull)
            .buildAsyncRetryer();

        retryer.call(notNullAfterAttempts(2));
        sleep(60L);
        assertFalse(executeBlockStrategy.get());
    }

    @Test
    public void testStopStrategy() throws Exception {
        AsyncRetryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(20L, TimeUnit.MILLISECONDS))
            .retryIfResult(Objects::isNull)
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .buildAsyncRetryer();

        AtomicBoolean test = new AtomicBoolean(false);
        retryer.call(notNullAfter5Attempts())
            .whenComplete((res, error) -> {
                assertNull(res);
                assertTrue(error instanceof RetryException);
                Attempt attempt = ((RetryException)error).getLastFailedAttempt();
                assertEquals(3, attempt.getAttemptNumber());
                test.set(true);
            });
        assertFalse(test.get());
        sleep(150L);
        assertTrue(test.get());

        test.set(false);
        retryer.call(notNullAfterAttempts(2))
            .whenComplete((res, error) -> {
                assertNull(error);
                assertNotNull(res);
                assertTrue(res);
                test.set(true);
            });
        assertFalse(test.get());
        sleep(150L);
        assertTrue(test.get());
    }

    @Test
    public void testRetryIfExceptionOf() throws Exception {
        AsyncRetryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(20L, TimeUnit.MILLISECONDS))
            .retryIfExceptionOfType(IllegalStateException.class)
            .buildAsyncRetryer();

        AtomicBoolean test = new AtomicBoolean(false);
        retryer.call(noIllegalStateExceptionAfter5Attempts())
            .whenComplete((res, error) -> {
                assertTrue(res);
                assertNull(error);
                test.set(true);
            });

        assertFalse(test.get());
        sleep(200L);
        assertTrue(test.get());
    }

    @Test
    public void testResultThrowException() throws Exception {
        AsyncRetryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(20L, TimeUnit.MILLISECONDS))
            .buildAsyncRetryer();

        AtomicBoolean test = new AtomicBoolean(false);
        retryer.call(noIllegalStateExceptionAfter5Attempts())
            .whenComplete((res, error) -> {
                assertNull(res);
                assertNotNull(error);
                assertTrue(error instanceof ExecutionException);
                assertTrue(error.getCause() instanceof IllegalStateException);
                test.set(true);
            });

        sleep(50L);
        assertTrue(test.get());
    }

    @Test
    public void testRetryListener() throws Exception {
        AtomicInteger value = new AtomicInteger(0);
        AtomicInteger listenOne = new AtomicInteger(0);
        AtomicInteger listenTwo = new AtomicInteger(0);
        AsyncRetryer<Integer> retryer = RetryerBuilder.<Integer>newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(10L, TimeUnit.MILLISECONDS))
            .retryIfResult(res -> res != 2)
            .withRetryListener(attempt -> listenOne.set(attempt.getResult()))
            .withRetryListener(attempt -> listenTwo.set(attempt.getResult()))
            .buildAsyncRetryer();

        retryer.call(value::get);

        assertEquals(0, listenOne.get());
        assertEquals(0, listenTwo.get());

        int x = value.incrementAndGet();
        sleep(20L);
        assertEquals(x, listenOne.get());
        assertEquals(x, listenTwo.get());

        x = value.incrementAndGet();
        sleep(20L);
        assertEquals(x, listenOne.get());
        assertEquals(x, listenTwo.get());

        x = value.incrementAndGet();
        sleep(20L);
        assertNotEquals(x, listenOne.get());
        assertNotEquals(x, listenTwo.get());
    }

    //-------------------------------------------------------------------------
    // private methods

    private long timeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private void sleep(long time) throws InterruptedException {
        Thread.sleep(time);
    }
}
