package com.github.lowzj.retry;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.github.lowzj.retry.attempt.Attempt;
import com.github.lowzj.retry.attempt.ExceptionAttempt;
import com.github.lowzj.retry.attempt.ResultAttempt;

/**
 * Created on 2018/2/11
 *
 * @author lowzj
 */
public class AsyncRetryer<V> {
    private final StopStrategy stopStrategy;
    private final WaitStrategy waitStrategy;
    private final AttemptTimeLimiter<V> attemptTimeLimiter;
    private final Predicate<Attempt<V>> rejectionPredicate;
    private final Collection<RetryListener<V>> listeners;
    private final ScheduledExecutorService executor;

    /**
     * Constructor
     *
     * @param attemptTimeLimiter to prevent from any single attempt from spinning infinitely
     * @param stopStrategy       the strategy used to decide when the retryer must stop retrying
     * @param waitStrategy       the strategy used to decide how much time to sleep between attempts
     * @param rejectionPredicate the predicate used to decide if the attempt must be rejected
     *                           or not. If an attempt is rejected, the retryer will retry the call, unless the stop
     *                           strategy indicates otherwise or the thread is interrupted.
     * @param listeners          collection of retry listeners
     * @param executor           to retry the call in thread pool
     */
    AsyncRetryer(AttemptTimeLimiter<V> attemptTimeLimiter,
                 StopStrategy stopStrategy,
                 WaitStrategy waitStrategy,
                 Predicate<Attempt<V>> rejectionPredicate,
                 Collection<RetryListener<V>> listeners,
                 ScheduledExecutorService executor) {
        Preconditions.assertNotNull(attemptTimeLimiter, "timeLimiter may not be null");
        Preconditions.assertNotNull(stopStrategy, "stopStrategy may not be null");
        Preconditions.assertNotNull(waitStrategy, "waitStrategy may not be null");
        Preconditions.assertNotNull(rejectionPredicate, "rejectionPredicate may not be null");
        Preconditions.assertNotNull(listeners, "listeners may not be null");
        Preconditions.assertNotNull(executor, "executor may not be null");

        this.attemptTimeLimiter = attemptTimeLimiter;
        this.stopStrategy = stopStrategy;
        this.waitStrategy = waitStrategy;
        this.rejectionPredicate = rejectionPredicate;
        this.listeners = listeners;
        this.executor = executor;
    }

    public CompletableFuture<V> call(Callable<V> callable) {
        CompletableFuture<V> resultFuture = new CompletableFuture<>();
        executor.execute(createRunner(callable, System.nanoTime(), 1, resultFuture));
        return resultFuture;
    }

    private Runnable createRunner(Callable<V> callable, long startTime, int attemptNumber,
                                  CompletableFuture<V> resultFuture) {
        return () -> {
            Attempt<V> attempt;
            try {
                V result = attemptTimeLimiter.call(callable);
                attempt = new ResultAttempt<>(result, attemptNumber,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
            } catch (Throwable t) {
                attempt = new ExceptionAttempt<>(t, attemptNumber,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
            }

            for (RetryListener<V> listener : listeners) {
                listener.onRetry(attempt);
            }

            if (!rejectionPredicate.test(attempt)) {
                try {
                    V result = attempt.get();
                    resultFuture.complete(result);
                } catch (ExecutionException e) {
                    resultFuture.completeExceptionally(e);
                }
            }

            if (resultFuture.isDone()) {
                return;
            }
            if (stopStrategy.shouldStop(attempt)) {
                resultFuture.completeExceptionally(new RetryException(attemptNumber, attempt));
            } else {
                executor.schedule(createRunner(callable, startTime, attemptNumber + 1, resultFuture),
                    TimeUnit.MILLISECONDS.toMicros(waitStrategy.computeSleepTime(attempt)),
                    TimeUnit.MICROSECONDS);
            }
        };
    }

}
