package com.bruce.limit.window;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 16:42
 * @Version 1.0
 */
public interface SampleWindow {

    SampleWindow addSample(long rtt, int inflight, boolean dropped);

    long getCandidateRttNanos();

    long getTrackedRttNanos();

    int getMaxInflight();

    int getSampleCount();

    boolean didDrop();
}
