package com.github.lowzj.retry.example;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.github.lowzj.retry.AsyncRetryer;
import com.github.lowzj.retry.ExecutorsUtil;
import com.github.lowzj.retry.RetryException;
import com.github.lowzj.retry.Retryer;
import com.github.lowzj.retry.RetryerBuilder;
import com.github.lowzj.retry.StopStrategies;
import com.github.lowzj.retry.WaitStrategies;
import org.junit.Test;

/**
 * Created on 2018/2/13
 *
 * @author lowzj
 */
public class Example {
    @Test
    public void retryerExample() {
        Retryer<Integer> retryer = RetryerBuilder.<Integer>newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(100L, TimeUnit.MILLISECONDS))
            .retryIfResult(num -> num != 5)
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(7))
            .build();

        try {
            retryer.call(noRuntimeExceptionAfter(4));
        } catch (ExecutionException | RetryException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void asyncRetryerExample() {
        AsyncRetryer<Integer> asyncRetryer = RetryerBuilder.<Integer>newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(100L, TimeUnit.MILLISECONDS))
            .retryIfResult(num -> num != 4)
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(7))
            .withExecutor(ExecutorsUtil.scheduledExecutorService("example", 1))
            .buildAsyncRetryer();

        CompletableFuture<Integer> future = asyncRetryer.call(noRuntimeExceptionAfter(3));

        // get the result asynchronously
        future.whenComplete((result, error) -> System.out.println(result));

        // or get the result synchronously
        try {
            System.out.println(future.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private Callable<Integer> noRuntimeExceptionAfter(final int attemptNumber) {
        return new Callable<Integer>() {
            private int count = 0;
            @Override
            public Integer call() throws Exception {
                if (count++ < attemptNumber) {
                    throw new RuntimeException("count[" + (count - 1) + "] < attemptNumber[" + attemptNumber + "]");
                }
                return count;
            }
        };
    }
}
