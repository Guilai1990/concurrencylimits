package com.bruce.limit;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 13:01
 * @Version 1.0
 */
public class SettableLimit extends AbstractLimit {

    public static SettableLimit startingAt(int limit) {
        return new SettableLimit(limit);
    }

    public SettableLimit(int limit) {
        super(limit);
    }

    @Override
    protected int _update(long startTime, long rtt, int inflight, boolean didDrop) {
        return getLimit();
    }

    public synchronized void setLimit(int limit) {
        super.setLimit(limit);
    }

    @Override
    public String toString() {
        return "SettableLimit [limit=" + getLimit() + "]";
    }
}
