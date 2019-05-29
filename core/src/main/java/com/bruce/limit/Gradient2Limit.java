package com.bruce.limit;

import com.bruce.MetricIds;
import com.bruce.MetricRegistry;
import com.bruce.internal.EmptyMetricRegistry;
import com.bruce.internal.Preconditions;
import com.bruce.limit.measurement.ExpAvgMeasurement;
import com.bruce.limit.measurement.Measurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 20:54
 * @Version 1.0
 */
public final class Gradient2Limit extends AbstractLimit {

    private static final Logger LOG = LoggerFactory.getLogger(Gradient2Limit.class);

    public static class Builder {
        private int initialLimit = 20;
        private int minLimit = 20;
        private int maxConcurrency = 200;

        private double smoothing = 0.2;
        private Function<Integer, Integer> queueSize = concurrency -> 4;
        private MetricRegistry registry = EmptyMetricRegistry.INSTANCE;
        private int longWindow = 600;
        private double rttTolerance = 1.5;

        public Builder initialLimit(int initialLimit) {
            this.initialLimit = initialLimit;
            return this;
        }

        public Builder minLimit(int minLimit) {
            this.minLimit = minLimit;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder queueSize(int queueSize) {
            this.queueSize = (ignore) -> {
                return queueSize;
            };
            return this;
        }

        public Builder queueSize(Function<Integer, Integer> queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder rttTolerance(double rttTolerance) {
            Preconditions.checkArgument(rttTolerance >= 1.0, "Tolerance must be >= 1.0");
            this.rttTolerance = rttTolerance;
            return this;
        }

        public Builder driftMultiplier(int multiplier) {
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

        public Builder longWindow(int n) {
            this.longWindow = n;
            return this;
        }

        public Gradient2Limit build() {
            return new Gradient2Limit(this);
        }

    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Gradient2Limit newDefault() {
        return newBuilder().build();
    }



    private volatile double estimateLimit;

    private long lastRtt;

    private final Measurement longRtt;

    private final int maxLimit;

    private final int minLimit;

    private final Function<Integer, Integer> queueSize;

    private final double smoothing;

    private final MetricRegistry.SampleListener longRttSampleListener;

    private final MetricRegistry.SampleListener shortRttSampleListener;

    private final MetricRegistry.SampleListener queueSizeSampleListener;

    private final double tolerance;

    private Gradient2Limit(Builder builder) {
        super(builder.initialLimit);

        this.estimateLimit = builder.initialLimit;
        this.maxLimit = builder.maxConcurrency;
        this.minLimit = builder.minLimit;
        this.queueSize = builder.queueSize;
        this.smoothing = builder.smoothing;
        this.tolerance = builder.rttTolerance;
        this.lastRtt = 0;
        this.longRtt = new ExpAvgMeasurement(builder.longWindow, 10);

        this.longRttSampleListener = builder.registry.registerDistribution(MetricIds.MIN_RTT_NAME);
        this.shortRttSampleListener = builder.registry.registerDistribution(MetricIds.WINDOW_MIN_RTT_NAME);
        this.queueSizeSampleListener = builder.registry.registerDistribution(MetricIds.WINDOW_QUEUE_SIZE_NAME);
    }

    @Override
    protected int _update(long startTime, long rtt, int inflight, boolean didDrop) {
        final double queueSize = this.queueSize.apply((int)this.estimateLimit);

        this.lastRtt = rtt;
        final double shortRtt = (double)rtt;
        final double longRtt = this.longRtt.add(rtt).doubleValue();

        shortRttSampleListener.addSample(shortRtt);
        longRttSampleListener.addSample(longRtt);
        queueSizeSampleListener.addSample(queueSize);

        if (longRtt / shortRtt > 2) {
            this.longRtt.update(current -> current.doubleValue() * 0.95);
        }

        if (inflight < estimateLimit / 2) {
            return (int)estimateLimit;
        }

        final double gradient = Math.max(0.5, Math.min(1.0, tolerance * longRtt / shortRtt));
        double newLimit = estimateLimit * gradient + queueSize;
        newLimit = estimateLimit*(1-smoothing) + newLimit * smoothing;
        newLimit = Math.max(minLimit, Math.min(maxLimit, newLimit));

        if ((int) estimateLimit != newLimit) {
            LOG.debug("New limit={} shortRtt={} ms longRtt=() ms queueSize={} gradient={}",
                    (int)newLimit,
                    getLastRtt(TimeUnit.MICROSECONDS)/1000.0,
                    getRttNoLoad(TimeUnit.MICROSECONDS)/1000.0,
                    queueSize,
                    gradient
                    );
        }

        estimateLimit = newLimit;

        return (int)estimateLimit;

    }

    public long getLastRtt(TimeUnit units) {
        return units.convert(lastRtt, TimeUnit.NANOSECONDS);
    }

    public long getRttNoLoad(TimeUnit units) {
        return units.convert(longRtt.get().longValue(), TimeUnit.NANOSECONDS);
    }

    @Override
    public String toString() {
        return "GradientLimit [limit=" + (int)estimateLimit + "]";
    }


}
