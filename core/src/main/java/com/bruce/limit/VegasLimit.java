package com.bruce.limit;

import com.bruce.MetricIds;
import com.bruce.MetricRegistry;
import com.bruce.internal.EmptyMetricRegistry;
import com.bruce.internal.Preconditions;
import com.bruce.limit.functions.Log10RootFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 15:07
 * @Version 1.0
 */
public class VegasLimit extends AbstractLimit {

    private static final Logger LOG = LoggerFactory.getLogger(VegasLimit.class);

    private static final Function<Integer, Integer> LOG10 = Log10RootFunction.create(0);

    public static class Builder {
        private int initialLimit = 20;
        private int maxConcurrency = 1000;
        private MetricRegistry registry = EmptyMetricRegistry.INSTANCE;
        private double smoothing = 1.0;

        private Function<Integer, Integer> alphaFunc = (limit) -> 3 * LOG10.apply(limit.intValue());
        private Function<Integer, Integer> betaFunc = (limit) -> 6 * LOG10.apply(limit.intValue());
        private Function<Integer, Integer> thresholdFunc = (limit) -> LOG10.apply(limit.intValue());
        private Function<Double, Double> increaseFunc = (limit) -> limit + LOG10.apply(limit.intValue());
        private Function<Double, Double> decreaseFunc = (limit) -> limit - LOG10.apply(limit.intValue());
        private int probeMultiplier = 30;

        private Builder() {}

        public Builder probeMultiplier(int probeMultiplier) {
            this.probeMultiplier = probeMultiplier;
            return this;
        }

        public Builder alpha(int alpha) {
            this.alphaFunc = (ignore) -> alpha;
            return this;
        }


        public Builder threashold(Function<Integer, Integer> threashold) {
            this.thresholdFunc = threashold;
            return this;
        }

        public Builder alpha(Function<Integer, Integer> alpha) {
            this.alphaFunc = alpha;
            return this;
        }

        public Builder beta(int beta) {
            this.betaFunc = (ignore) -> beta;
            return this;
        }

        public Builder beta(Function<Integer, Integer> beta) {
            this.betaFunc = beta;
            return this;
        }

        public Builder increase(Function<Double, Double> increase) {
            this.increaseFunc = increase;
            return this;
        }

        public Builder decrease(Function<Double, Double> decrease) {
            this.decreaseFunc = decrease;
            return this;
        }

        public Builder smoothing(double smoothing) {
            this.smoothing = smoothing;
            return this;
        }

        public Builder initialLimit(int initialLimit) {
            this.initialLimit = initialLimit;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder metricRegistry(MetricRegistry registry) {
            this.registry = registry;
            return this;
        }

        public VegasLimit build() {
            return new VegasLimit(this);
        }

    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static VegasLimit newDefault() {
        return newBuilder().build();
    }

    private volatile double estimateLimit;

    private volatile long rtt_noload = 0;

    private final int maxLimit;
    private final double smoothing;
    private final Function<Integer, Integer> alphaFunc;
    private final Function<Integer, Integer> betaFunc;
    private final Function<Integer, Integer> thresholdFunc;
    private final Function<Double, Double> increaseFunc;
    private final Function<Double, Double> decreaseFunc;
    private final MetricRegistry.SampleListener rttSampleListener;
    private final int probeMultiplier;
    private int probeCount = 0;
    private double probeJitter;

    private VegasLimit(Builder builder) {
        super(builder.initialLimit);
        this.estimateLimit = builder.initialLimit;
        this.maxLimit = builder.maxConcurrency;
        this.alphaFunc = builder.alphaFunc;
        this.betaFunc = builder.betaFunc;
        this.increaseFunc = builder.increaseFunc;
        this.decreaseFunc = builder.decreaseFunc;
        this.thresholdFunc = builder.thresholdFunc;
        this.smoothing = builder.smoothing;
        this.probeMultiplier = builder.probeMultiplier;

        resetProbeJitter();

        this.rttSampleListener = builder.registry.registerDistribution(MetricIds.MIN_RTT_NAME);
    }

    private void resetProbeJitter() {
        probeJitter = ThreadLocalRandom.current().nextDouble(0.5, 1);
    }

    private boolean shouldProbe() {
        return probeJitter * probeMultiplier * estimateLimit <= probeCount;
    }

    @Override
    protected int _update(long startTime, long rtt, int inflight, boolean didDrop) {

        Preconditions.checkArgument(rtt > 0, "rtt must be > 0 but got " + rtt);

        probeCount++;
        if (shouldProbe()) {
            LOG.debug("Probe MinRTT {}", TimeUnit.NANOSECONDS.toMicros(rtt) / 1000.0);
            resetProbeJitter();
            probeCount = 0;
            rtt_noload = rtt;
            return (int)estimateLimit;
        }

        if (rtt_noload == 0 || rtt < rtt_noload) {
            LOG.debug("New MinRTT {} ", TimeUnit.NANOSECONDS.toMicros(rtt) / 1000.0);
            rtt_noload = rtt;
            return (int)estimateLimit;
        }

        rttSampleListener.addSample(rtt_noload);

        return updateEstimatedLimit(rtt, inflight, didDrop);
    }

    private int updateEstimatedLimit(long rtt, int inflight, boolean didDrop) {
        final int queueSize = (int)Math.ceil(estimateLimit * (1 - (double)rtt_noload / rtt));

        double newLimit;
        if (didDrop) {
            newLimit = decreaseFunc.apply(estimateLimit);
        } else if (inflight * 2 < estimateLimit) {
            return (int)estimateLimit;
        } else {
            int alpha = alphaFunc.apply((int)estimateLimit);
            int beta = betaFunc.apply((int)estimateLimit);
            int threshold = this.thresholdFunc.apply((int)estimateLimit);

            if (queueSize <= threshold) {
                newLimit = estimateLimit + beta;
            } else if (queueSize < alpha) {
                newLimit = increaseFunc.apply(estimateLimit);
            } else if (queueSize > beta) {
                newLimit = decreaseFunc.apply(estimateLimit);
            } else {
                return (int)estimateLimit;
            }
        }

        newLimit = Math.max(1, Math.min(maxLimit, newLimit));
        newLimit = (1 - smoothing) * estimateLimit + smoothing * newLimit;
        if ((int)newLimit != (int)estimateLimit && LOG.isDebugEnabled()) {
            LOG.debug("New limit={} minRtt={} ms winRtt={} ms queueSiz={}",
                    (int)newLimit,
                    TimeUnit.NANOSECONDS.toMicros(rtt_noload) / 1000.0,
                    TimeUnit.NANOSECONDS.toMicros(rtt) / 1000.0,
                    queueSize);
        }
        estimateLimit = newLimit;
        return (int)estimateLimit;
    }

    @Override
    public String toString() {
        return "VegasLimit [limit=" + getLimit() +
                ", rtt_noload=" + TimeUnit.NANOSECONDS.toMicros(rtt_noload) / 1000.0 +
                " ms]";
    }

}
