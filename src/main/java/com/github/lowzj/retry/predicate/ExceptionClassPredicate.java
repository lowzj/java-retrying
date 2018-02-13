package com.github.lowzj.retry.predicate;

import java.util.function.Predicate;

import com.github.lowzj.retry.attempt.Attempt;

/**
 * Created on 2018/2/11
 *
 * @author lowzj
 */
public class ExceptionClassPredicate<V> implements Predicate<Attempt<V>> {
    private Class<? extends Throwable> exceptionClass;

    public ExceptionClassPredicate(Class<? extends Throwable> exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    @Override
    public boolean test(Attempt<V> attempt) {
        return attempt.hasException() && exceptionClass.isAssignableFrom(attempt.getExceptionCause().getClass());
    }
}
