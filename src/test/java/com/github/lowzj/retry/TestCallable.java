package com.github.lowzj.retry;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

/**
 * Created on 2018/2/12
 *
 * @author lowzj
 */
final class TestCallable {

    static Callable<Boolean> noIllegalStateExceptionAfter5Attempts() {
        return new Callable<Boolean>() {
            int counter = 0;

            @Override
            public Boolean call() throws Exception {
                if (counter < 5) {
                    counter++;
                    throw new IllegalStateException();
                }
                return true;
            }
        };
    }

    static Callable<Boolean> noIOExceptionAfter5Attempts() {
        return new Callable<Boolean>() {
            int counter = 0;

            @Override
            public Boolean call() throws IOException {
                if (counter < 5) {
                    counter++;
                    throw new IOException();
                }
                return true;
            }
        };
    }

    static Callable<Boolean> notNullAfter5Attempts() {
        return notNullAfterAttempts(5);
    }

    static Callable<Boolean> notNullAfterAttempts(final int attemptNumber) {
        return new Callable<Boolean>() {
            int counter = 0;

            @Override
            public Boolean call() throws Exception {
                if (counter < attemptNumber) {
                    counter++;
                    return null;
                }
                return true;
            }
        };
    }

    static Callable<Boolean> notNullResultOrIOExceptionOrRuntimeExceptionAfter5Attempts() {
        return new Callable<Boolean>() {
            int counter = 0;

            @Override
            public Boolean call() throws IOException {
                if (counter < 1) {
                    counter++;
                    return null;
                } else if (counter < 2) {
                    counter++;
                    throw new IOException();
                } else if (counter < 5) {
                    counter++;
                    throw new IllegalStateException();
                }
                return true;
            }
        };
    }

    static Callable<Boolean> alwaysNull(final CountDownLatch latch) {
        return () -> {
            latch.countDown();
            return null;
        };
    }
}
