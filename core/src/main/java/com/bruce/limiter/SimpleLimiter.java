package com.bruce.limiter;

import com.bruce.MetricIds;
import com.bruce.MetricRegistry;
import com.bruce.limit.AbstractLimit;

import java.util.Optional;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 21:59
 * @Version 1.0
 */
public class SimpleLimiter<ContextT> extends AbstractLimiter<ContextT> {

    public static class Builder extends AbstractLimiter.Builder<Builder> {
        public <ContextT> SimpleLimiter<ContextT> build() {
            return new SimpleLimiter<>(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    private final MetricRegistry.SampleListener inflightDistribution;

    public SimpleLimiter(AbstractLimiter.Builder<?> builder) {
        super(builder);

        this.inflightDistribution = builder.registry.registerDistribution(MetricIds.INFLIGHT_NAME);
    }


    @Override
    public Optional<Listener> acquire(ContextT context) {
        int currentInFlight = getInflight();
        inflightDistribution.addSample(currentInFlight);
        if (currentInFlight >= getLimit()) {
            return Optional.empty();
        }
        return Optional.of(createListener());
    }
}
