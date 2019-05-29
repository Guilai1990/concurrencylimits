package com.bruce.limit;

import com.bruce.Limit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 19:59
 * @Version 1.0
 */
public abstract class AbstractLimit implements Limit {

    private volatile int limit;

    private final List<Consumer<Integer>> listeners = new CopyOnWriteArrayList<>();

    protected AbstractLimit(int initialLimit) {
        this.limit = initialLimit;
    }

    @Override
    public int getLimit() {
        return limit;
    }

    @Override
    public void notifyOnChange(Consumer<Integer> consumer) {
        this.listeners.add(consumer);
    }

    @Override
    public void onSample(long startTime, long rtt, int inflight, boolean didDrop) {
        setLimit(_update(startTime, rtt, inflight, didDrop));
    }

    protected abstract int _update(long startTime, long rtt, int inflight, boolean didDrop);

    protected synchronized void setLimit(int newLimit) {
        if (newLimit != limit) {
            limit = newLimit;
            listeners.forEach(listener -> listener.accept(newLimit));
        }
    }

}
