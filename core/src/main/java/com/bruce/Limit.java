package com.bruce;

import java.util.function.Consumer;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 19:36
 * @Version 1.0
 */
public interface Limit {

    int getLimit();

    void notifyOnChange(Consumer<Integer> consumer);

    void onSample(long startTime, long rtt, int inflight, boolean didDrop);
}
