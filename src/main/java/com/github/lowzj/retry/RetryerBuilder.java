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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;

import com.github.lowzj.retry.attempt.Attempt;
import com.github.lowzj.retry.predicate.ExceptionClassPredicate;
import com.github.lowzj.retry.predicate.ExceptionPredicate;
import com.github.lowzj.retry.predicate.ResultPredicate;

/**
 * A builder used to configure and create a {@link Retryer}.
 *
 * @param <V> result of a {@link Retryer}'s call, the type of the call return value
 * @author JB
 * @author Jason Dunkelberger (dirkraft)
 */
public class RetryerBuilder<V> {
    private AttemptTimeLimiter<V> attemptTimeLimiter;
    private StopStrategy stopStrategy;
    private WaitStrategy waitStrategy;
    private BlockStrategy blockStrategy;
    private Predicate<Attempt<V>> rejectionPredicate = vAttempt -> false;
    private List<RetryListener<V>> listeners = new ArrayList<>();
    private ScheduledExecutorService executor;

    private RetryerBuilder() {
    }

    /**
     * Constructs a new builder
     *
     * @param <V> result of a {@link Retryer}'s call, the type of the call return value
     * @return the new builder
     */
    public static <V> RetryerBuilder<V> newBuilder() {
        return new RetryerBuilder<V>();
    }

    /**
     * Adds a listener that will be notified of each attempt that is made
     *
     * @param listener Listener to add
     * @return <code>this</code>
     */
    public RetryerBuilder<V> withRetryListener(RetryListener<V> listener) {
        Preconditions.assertNotNull(listener, "listener may not be null");
        listeners.add(listener);
        return this;
    }

    /**
     * Sets the wait strategy used to decide how long to sleep between failed attempts.
     * The default strategy is to retry immediately after a failed attempt.
     *
     * @param waitStrategy the strategy used to sleep between failed attempts
     * @return <code>this</code>
     * @throws IllegalStateException if a wait strategy has already been set.
     */
    public RetryerBuilder<V> withWaitStrategy(WaitStrategy waitStrategy) throws IllegalStateException {
        Preconditions.assertNotNull(waitStrategy, "waitStrategy may not be null");
        Preconditions.checkState(this.waitStrategy == null, "a wait strategy has already been set %s", this.waitStrategy);
        this.waitStrategy = waitStrategy;
        return this;
    }

    /**
     * Sets the stop strategy used to decide when to stop retrying. The default strategy is to not stop at all .
     *
     * @param stopStrategy the strategy used to decide when to stop retrying
     * @return <code>this</code>
     * @throws IllegalStateException if a stop strategy has already been set.
     */
    public RetryerBuilder<V> withStopStrategy(StopStrategy stopStrategy) throws IllegalStateException {
        Preconditions.assertNotNull(stopStrategy, "stopStrategy may not be null");
        Preconditions.checkState(this.stopStrategy == null, "a stop strategy has already been set %s", this.stopStrategy);
        this.stopStrategy = stopStrategy;
        return this;
    }


    /**
     * Sets the block strategy used to decide how to block between retry attempts. The default strategy is to use Thread#sleep().
     *
     * @param blockStrategy the strategy used to decide how to block between retry attempts
     * @return <code>this</code>
     * @throws IllegalStateException if a block strategy has already been set.
     */
    public RetryerBuilder<V> withBlockStrategy(BlockStrategy blockStrategy) throws IllegalStateException {
        Preconditions.assertNotNull(blockStrategy, "blockStrategy may not be null");
        Preconditions.checkState(this.blockStrategy == null, "a block strategy has already been set %s", this.blockStrategy);
        this.blockStrategy = blockStrategy;
        return this;
    }


    /**
     * Configures the retryer to limit the duration of any particular attempt by the given duration.
     *
     * @param attemptTimeLimiter to apply to each attempt
     * @return <code>this</code>
     */
    public RetryerBuilder<V> withAttemptTimeLimiter(AttemptTimeLimiter<V> attemptTimeLimiter) {
        Preconditions.assertNotNull(attemptTimeLimiter);
        this.attemptTimeLimiter = attemptTimeLimiter;
        return this;
    }

    public RetryerBuilder<V> withExecutor(ScheduledExecutorService executor) {
        Preconditions.assertNotNull(executor);
        this.executor = executor;
        return this;
    }

    /**
     * Configures the retryer to retry if an exception (i.e. any <code>Exception</code> or subclass
     * of <code>Exception</code>) is thrown by the call.
     *
     * @return <code>this</code>
     */
    public RetryerBuilder<V> retryIfException() {
        rejectionPredicate = rejectionPredicate.or(new ExceptionClassPredicate<>(Exception.class));
        return this;
    }

    /**
     * Configures the retryer to retry if a runtime exception (i.e. any <code>RuntimeException</code> or subclass
     * of <code>RuntimeException</code>) is thrown by the call.
     *
     * @return <code>this</code>
     */
    public RetryerBuilder<V> retryIfRuntimeException() {
        rejectionPredicate = rejectionPredicate.or(new ExceptionClassPredicate<>(RuntimeException.class));
        return this;
    }

    /**
     * Configures the retryer to retry if an exception of the given class (or subclass of the given class) is
     * thrown by the call.
     *
     * @param exceptionClass the type of the exception which should cause the retryer to retry
     * @return <code>this</code>
     */
    public RetryerBuilder<V> retryIfExceptionOfType(Class<? extends Throwable> exceptionClass) {
        Preconditions.assertNotNull(exceptionClass, "exceptionClass may not be null");
        rejectionPredicate = rejectionPredicate.or(new ExceptionClassPredicate<>(exceptionClass));
        return this;
    }

    /**
     * Configures the retryer to retry if an exception satisfying the given predicate is
     * thrown by the call.
     *
     * @param exceptionPredicate the predicate which causes a retry if satisfied
     * @return <code>this</code>
     */
    public RetryerBuilder<V> retryIfException(Predicate<Throwable> exceptionPredicate) {
        Preconditions.assertNotNull(exceptionPredicate, "exceptionPredicate may not be null");
        rejectionPredicate = rejectionPredicate.or(new ExceptionPredicate<>(exceptionPredicate));
        return this;
    }

    /**
     * Configures the retryer to retry if the result satisfies the given predicate.
     *
     * @param resultPredicate a predicate applied to the result, and which causes the retryer
     *                        to retry if the predicate is satisfied
     * @return <code>this</code>
     */
    public RetryerBuilder<V> retryIfResult(Predicate<V> resultPredicate) {
        Preconditions.assertNotNull(resultPredicate, "resultPredicate may not be null");
        rejectionPredicate = rejectionPredicate.or(new ResultPredicate<V>(resultPredicate));
        return this;
    }

    /**
     * Builds the retryer.
     *
     * @return the built retryer.
     */
    public Retryer<V> build() {
        AttemptTimeLimiter<V> theAttemptTimeLimiter = attemptTimeLimiter == null ? AttemptTimeLimiters.<V>noTimeLimit()
            : attemptTimeLimiter;
        StopStrategy theStopStrategy = stopStrategy == null ? StopStrategies.neverStop() : stopStrategy;
        WaitStrategy theWaitStrategy = waitStrategy == null ? WaitStrategies.noWait() : waitStrategy;
        BlockStrategy theBlockStrategy = blockStrategy == null ? BlockStrategies.threadSleepStrategy() : blockStrategy;

        return new Retryer<V>(theAttemptTimeLimiter, theStopStrategy, theWaitStrategy, theBlockStrategy,
            rejectionPredicate, listeners);
    }

    /**
     * @return the built AsyncRetryer
     */
    public AsyncRetryer<V> buildAsyncRetryer() {
        AttemptTimeLimiter<V> theAttemptTimeLimiter = attemptTimeLimiter == null ? AttemptTimeLimiters.noTimeLimit()
            : attemptTimeLimiter;
        StopStrategy theStopStrategy = stopStrategy == null ? StopStrategies.neverStop() : stopStrategy;
        WaitStrategy theWaitStrategy = waitStrategy == null ? WaitStrategies.noWait() : waitStrategy;
        ScheduledExecutorService theExecutor = executor == null ? ExecutorsUtil.scheduledExecutorService(
            "default-async-retry", 5) : executor;

        return new AsyncRetryer<>(theAttemptTimeLimiter, theStopStrategy, theWaitStrategy, rejectionPredicate,
            listeners, theExecutor);
    }

}
