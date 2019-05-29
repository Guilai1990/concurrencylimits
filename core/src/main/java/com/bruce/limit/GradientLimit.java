package com.bruce.limit;

import com.bruce.MetricIds;
import com.bruce.MetricRegistry;
import com.bruce.internal.EmptyMetricRegistry;
import com.bruce.internal.Preconditions;
import com.bruce.limit.functions.SquareRootFunction;
import com.bruce.limit.measurement.Measurement;
import com.sun.javafx.font.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 23:19
 * @Version 1.0
 */
public class GradientLimit extends AbstractLimit {

    private static final int DISABLE = -1;

    private static final Logger LOG = LoggerFactory.getLogger(GradientLimit.class);

    public static class Builder {
        private int initialLimit = 50;
        private int minLimit = 1;
        private int maxCoucurrency = 1000;

        private double smoothing = 0.2;
        private Function<Integer, Integer> queueSize = SquareRootFunction.create(4);
        private MetricRegistry registry = EmptyMetricRegistry.INSTANCE;
        private double rttTolerance = 2.0;
        private int probeInterval = 1000;

        public Builder initialLimit(int initialLimit) {
            this.initialLimit = initialLimit;
            return this;
        }

        public Builder minLimit(int minLimit) {
            this.minLimit = minLimit;
            return this;
        }

        public Builder rttTolerance(double rttTolerance) {
            Preconditions.checkArgument(rttTolerance >= 1.0, "Tolerance must be >= 1.0");
            this.rttTolerance = rttTolerance;
            return this;
        }

        public Builder maxCoucurrency(int maxCoucurrency) {
            this.maxCoucurrency = maxCoucurrency;
            return this;
        }

        public Builder queueSize(int queueSize) {
            this.queueSize = (ignore) -> queueSize;
            return this;
        }

        public Builder queueSize(Function<Integer, Integer> queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder smoothing(double smoothing) {
            this.smoothing = smoothing;
            return this;
        }

        public Builder metricRegistry(MetricRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder probeInterval(int probeInterval) {
            this.probeInterval = probeInterval;
            return this;
        }

        public GradientLimit build() {
            return new GradientLimit(this);
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static GradientLimit newDefault() {
        return newBuilder().build();
    }



    private volatile double estimateLimit;

    private long lastRtt = 0;

    private final Measurement rttNoLoadMeasurement;

    private final int maxLimit;

    private final int minLimit;

    private final Function<Integer, Integer> queueSize;

    private final double smoothing;

    private final double rttTolerance;

    private final MetricRegistry.SampleListener minRttSampleListener;

    private final MetricRegistry.SampleListener minWindowSampleListener;

    private final MetricRegistry.SampleListener queueSizeSampleListener;

    private final int probeInterval;

    private int resetRttCounter;

    private GradientLimit(Builder builder) {
        super(builder.initialLimit);
        this.estimateLimit = builder.initialLimit;
        this.maxLimit = builder.maxCoucurrency;
        this.minLimit = builder.minLimit;
        this.queueSize = builder.queueSize;
        this.smoothing = builder.smoothing;
        this.rttTolerance = builder.rttTolerance;
        this.probeInterval = builder.probeInterval;
        this.resetRttCounter = nextProbeCountdown();
        this.rttNoLoadMeasurement = new MinimumMeasurement();

        this.minRttSampleListener = builder.registry.registerDistribution(MetricIds.MIN_RTT_NAME);
        this.minWindowSampleListener = builder.registry.registerDistribution(MetricIds.WINDOW_MIN_RTT_NAME);
        this.queueSizeSampleListener = builder.registry.registerDistribution(MetricIds.WINDOW_QUEUE_SIZE_NAME);
    }

    private int nextProbeCountdown() {
        if (probeInterval == DISABLE) {
            return DISABLE;
        }
        return probeInterval + ThreadLocalRandom.current().nextInt(probeInterval);
    }

    @Override
    protected int _update(final long startTime, final long rtt, final int inflight, final boolean didDrop) {
        lastRtt = rtt;
        minWindowSampleListener.addSample(rtt);

        final double queueSize = this.queueSize.apply((int)this.estimateLimit);
        queueSizeSampleListener.addSample(queueSize);

        if (probeInterval != DISABLE && resetRttCounter-- <= 0) {
            resetRttCounter = nextProbeCountdown();

            estimateLimit = Math.max(minLimit, queueSize);
            rttNoLoadMeasurement.reset();
            lastRtt = 0;
            LOG.debug("Probe MinRTT limit={}", getLimit());
            return (int)estimateLimit;
        }

        final long rttNoLoad = rttNoLoadMeasurement.add(rtt).longValue();
        minRttSampleListener.addSample(rttNoLoad);

        final double gradient = Math.max(0.5, Math.min(1.0, rttTolerance * rttNoLoad / rtt));

        double newLimit;
        if (didDrop) {
            newLimit = estimateLimit/2;
        } else if(inflight < estimateLimit / 2) {
            return (int)estimateLimit;
        } else {
            newLimit = estimateLimit * gradient + queueSize;
        }

        if (newLimit < estimateLimit) {
            newLimit = Math.max(minLimit, estimateLimit * (1-smoothing) + smoothing*(newLimit));
        }
        newLimit = Math.max(queueSize, Math.min(maxLimit, newLimit));

        if ((int)newLimit != (int)estimateLimit && LOG.isErrorEnabled()) {
            LOG.debug("New limit={} minRtt={} ms winRtt={} ms queueSize={} gradient={} resetCounter={}",
                    (int)newLimit,
                    TimeUnit.NANOSECONDS.toMicros(rttNoLoad)/1000.0,
                    TimeUnit.NANOSECONDS.toMicros(rtt)/1000.0,
                    queueSize,
                    gradient,
                    resetRttCounter);
        }

        estimateLimit = newLimit;
        return (int)estimateLimit;

    }

    public long getLastRtt(TimeUnit units) {
        return units.convert(lastRtt, TimeUnit.NANOSECONDS);
    }

    public long getRttNoLoad(TimeUnit units) {
        return units.convert(rttNoLoadMeasurement.get().longValue(), TimeUnit.NANOSECONDS);
    }

    @Override
    public String toString() {
        return "GradientLimit [limit=" + (int)estimateLimit +
                ", rtt_noload=" + TimeUnit.MICROSECONDS.toMillis(rttNoLoadMeasurement.get().longValue()) / 1000.0 +
                " ms]";
    }


}
