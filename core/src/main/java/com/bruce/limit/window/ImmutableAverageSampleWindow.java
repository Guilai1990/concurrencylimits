package com.bruce.limit.window;

import com.sun.scenario.effect.impl.prism.PrImage;

import java.util.concurrent.TimeUnit;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 16:45
 * @Version 1.0
 */
public class ImmutableAverageSampleWindow implements SampleWindow {

    private final long minRtt;
    private final long sum;
    private final int maxInFlight;
    private final int sampleCount;
    private final boolean didDrop;

    public ImmutableAverageSampleWindow() {
        this.minRtt = Long.MAX_VALUE;
        this.sum = 0;
        this.maxInFlight = 0;
        this.sampleCount = 0;
        this.didDrop = false;
    }

    public ImmutableAverageSampleWindow(long minRtt, long sum, int maxInFlight, int sampleCount, boolean didDrop) {
        this.minRtt = minRtt;
        this.sum = sum;
        this.maxInFlight = maxInFlight;
        this.sampleCount = sampleCount;
        this.didDrop = didDrop;
    }

    @Override
    public ImmutableAverageSampleWindow addSample(long rtt, int inflight, boolean dropped) {
        return new ImmutableAverageSampleWindow(
                Math.min(rtt, minRtt),
                sum+rtt,
                Math.max(inflight, this.maxInFlight),
                sampleCount + 1,
                this.didDrop || didDrop
        );
    }

    @Override
    public long getCandidateRttNanos() {
        return minRtt;
    }

    @Override
    public long getTrackedRttNanos() {
        return sampleCount == 0 ? 0 : sum/sampleCount;
    }

    @Override
    public int getMaxInflight() {
        return maxInFlight;
    }

    @Override
    public int getSampleCount() {
        return sampleCount;
    }

    @Override
    public boolean didDrop() {
        return didDrop;
    }

    @Override
    public String toString() {
        return "ImmutableAverageSampleWindow ["
                + "minRtt=" + TimeUnit.NANOSECONDS.toMicros(minRtt) / 1000.0
                + ", avgRtt=" + TimeUnit.NANOSECONDS.toMicros(getTrackedRttNanos()) / 1000.0
                + ", maxInflight=" + maxInFlight
                + ", sampleCount=" + sampleCount
                + ", didDrop=" + didDrop + "]";
    }
}
