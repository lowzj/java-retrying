# java-retrying

[![Build Status](https://travis-ci.org/lowzj/java-retrying.svg?branch=master)](https://travis-ci.org/lowzj/java-retrying)
[![codecov](https://codecov.io/gh/lowzj/java-retrying/branch/master/graph/badge.svg)](https://codecov.io/gh/lowzj/java-retrying)

java重试, 支持同步/异步, 简单灵活可配, 不依赖第三方库.

基于[guava-retrying](https://github.com/rholder/guava-retrying)改造, 增加了异步重试, 同时去掉了第三方依赖, 使用方法基本一致.

名称 | JDK | 第三方依赖 | 同步重试 | 异步重试
---- | --- | ---- | -------- | --------
guava-retrying | 大于等于6 | guava,findbugs | Y | N
java-retrying | 大于等于8 | 无 | Y | Y

## Quickstart


* 同步重试
```java
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
```

* 异步重试
```java
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
```

其中函数`noRuntimeExceptionAfter`如下:
```java
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
```
