package com.bruce.limit;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 20:38
 * @Version 1.0
 */
public class FixedLimit extends AbstractLimit {

    public static FixedLimit of(int limit) {
        return new FixedLimit(limit);
    }

    private FixedLimit(int limit) {
        super(limit);
    }

    @Override
    protected int _update(long startTime, long rtt, int inflight, boolean didDrop) {
        return getLimit();
    }

    @Override
    public String toString() {
        return "FixedLimit [limit=" + getLimit() + "]";
    }

}
