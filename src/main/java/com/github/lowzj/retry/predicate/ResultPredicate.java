package com.github.lowzj.retry.predicate;

import java.util.function.Predicate;

import com.github.lowzj.retry.attempt.Attempt;

/**
 * Created on 2018/2/11
 *
 * @author lowzj
 */
public class ResultPredicate<V> implements Predicate<Attempt<V>> {

    private Predicate<V> delegate;

    public ResultPredicate(Predicate<V> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean test(Attempt<V> attempt) {
        if (!attempt.hasResult()) {
            return false;
        }
        V result = attempt.getResult();
        return delegate.test(result);
    }
}
