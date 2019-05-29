package com.bruce.limiter;

import com.bruce.Limit;
import com.bruce.Limiter;
import com.bruce.MetricIds;
import com.bruce.MetricRegistry;
import com.bruce.internal.EmptyMetricRegistry;
import com.bruce.limit.VegasLimit;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 21:59
 * @Version 1.0
 */
public abstract class AbstractLimiter<ContextT> implements Limiter<ContextT> {

    public abstract static class Builder<BuilderT extends Builder<BuilderT>> {
        private Limit limit = VegasLimit.newDefault();
        private Supplier<Long> clock = System::nanoTime;
        protected MetricRegistry registry = EmptyMetricRegistry.INSTANCE;

        public BuilderT limit(Limit limit) {
            this.limit = limit;
            return self();
        }

        public Builder clock(Supplier<Long> clock) {
            this.clock = clock;
            return self();
        }

        public BuilderT metricRegistry(MetricRegistry registry) {
            this.registry = registry;
            return self();
        }

        protected abstract BuilderT self();

    }

    private final AtomicInteger inFlight = new AtomicInteger();
    private final Supplier<Long> clock;
    private final Limit limitAlgorithm;
    private volatile int limit;

    public AbstractLimiter(Builder<?> builder) {
        this.clock = builder.clock;
        this.limitAlgorithm = builder.limit;
        this.limit = limitAlgorithm.getLimit();
        this.limitAlgorithm.notifyOnChange(this::onNewLimit);

        builder.registry.registerGauge(MetricIds.LIMIT_NAME, this::getLimit);
    }

    protected Listener createListener() {
        final long startTime = clock.get();
        final int currentInflight = inFlight.incrementAndGet();
        return new Listener() {
            @Override
            public void onSuccess() {
                inFlight.decrementAndGet();

                limitAlgorithm.onSample(startTime, clock.get() - startTime, currentInflight, false);
            }

            @Override
            public void onIgnore() {
                inFlight.decrementAndGet();
            }

            @Override
            public void onDropped() {
                inFlight.decrementAndGet();

                limitAlgorithm.onSample(startTime, clock.get() - startTime, currentInflight, true);
            }
        };

    }

    public int getLimit() {
        return limit;
    }

    public int getInflight() {
        return inFlight.get();
    }

    public void onNewLimit(int newLimit) {
        limit = newLimit;
    }


}
