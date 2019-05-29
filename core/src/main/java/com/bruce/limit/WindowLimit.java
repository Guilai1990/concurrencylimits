package com.bruce.limit;

import com.bruce.Limit;
import com.bruce.internal.Preconditions;
import com.bruce.limit.window.AverageSampleWindowFactory;
import com.bruce.limit.window.SampleWindow;
import com.bruce.limit.window.SampleWindowFactory;

import javax.swing.plaf.PanelUI;
import java.sql.Time;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 16:33
 * @Version 1.0
 */
public class WindowLimit implements Limit {

    private static final long DEFAULT_MIN_WINDOW_TIME = TimeUnit.SECONDS.toNanos(1);
    private static final long DEFAULT_MAX_WINDOW_TIME = TimeUnit.SECONDS.toNanos(1);
    private static final long DEFAULT_MIN_RTT_THRESHOLD = TimeUnit.MICROSECONDS.toNanos(100);
    private static final int DEFAULT_WINDOW_SIZE = 10;

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private long maxWindowTime = DEFAULT_MAX_WINDOW_TIME;
        private long minWindowTime = DEFAULT_MIN_WINDOW_TIME;
        private int windowSize = DEFAULT_WINDOW_SIZE;
        private long minRttThreashold = DEFAULT_MIN_RTT_THRESHOLD;
        private SampleWindowFactory sampleWindowFactory = AverageSampleWindowFactory.create();

        public Builder minWindowTime(long minWindowTime, TimeUnit units) {
            Preconditions.checkArgument(units.toMillis(minWindowTime) >= 100,"minWindowTime must be >= 100 ms");
            this.minWindowTime = units.toNanos(minWindowTime);
            return this;
        }

        public Builder maxWindowTime(long maxWindowTime, TimeUnit units) {
            Preconditions.checkArgument(units.toMillis(maxWindowTime) >= 100, "maxWindowTime must be >= 100 ms");
            this.maxWindowTime = units.toNanos(maxWindowTime);
            return this;
        }

        public Builder windowSize(int windowSize) {
            Preconditions.checkArgument(windowSize >= 10, "Window size must be >= 10");
            this.windowSize = windowSize;
            return this;
        }

        public Builder minRttThreshold(long threashold, TimeUnit units) {
            this.minRttThreashold = units.toNanos(threashold);
            return this;
        }

        public Builder sampleWindowFactory(SampleWindowFactory sampleWindowFactory) {
            this.sampleWindowFactory = sampleWindowFactory;
            return this;
        }

        public WindowLimit build(Limit delegate) {
            return new WindowLimit(this, delegate);
        }
    }

    private final Limit delegate;

    private volatile long nextUpdateTime = 0;

    private final long minWindowTime;

    private final long maxWindowTIme;

    private final int windowSize;

    private final long minRttThreshold;

    private final Object lock = new Object();

    private final SampleWindowFactory sampleWindowFactory;

    private final AtomicReference<SampleWindow> sample;

    private WindowLimit(Builder builder, Limit delegate) {
        this.delegate = delegate;
        this.minWindowTime = builder.minWindowTime;
        this.maxWindowTIme = builder.maxWindowTime;
        this.windowSize = builder.windowSize;
        this.minRttThreshold = builder.minRttThreashold;
        this.sampleWindowFactory = builder.sampleWindowFactory;
        this.sample = new AtomicReference<>(sampleWindowFactory.newInstance());
    }


    @Override
    public int getLimit() {
        return delegate.getLimit();
    }

    @Override
    public void notifyOnChange(Consumer<Integer> consumer) {
        delegate.notifyOnChange(consumer);
    }

    @Override
    public void onSample(long startTime, long rtt, int inflight, boolean didDrop) {
        long endTime = startTime + rtt;

        if (rtt < minRttThreshold) {
            return;
        }

        sample.updateAndGet(current -> current.addSample(rtt, inflight, didDrop));

        if (endTime > nextUpdateTime) {
            synchronized (lock) {
                if (endTime > nextUpdateTime) {
                    SampleWindow current = sample.getAndSet(sampleWindowFactory.newInstance());
                    nextUpdateTime = endTime + Math.min(Math.max(current.getCandidateRttNanos() * 2, minWindowTime), maxWindowTIme);

                    if (isWindowReady(current)) {
                        delegate.onSample(startTime, current.getTrackedRttNanos(), current.getMaxInflight(), current.didDrop());
                    }
                }
            }
        }
    }

    private boolean isWindowReady(SampleWindow sample) {
        return sample.getCandidateRttNanos() < Long.MAX_VALUE && sample.getSampleCount() >= windowSize;
    }


}
