package com.bruce.limit;

import com.bruce.Limit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 13:04
 * @Version 1.0
 */
public class TracingLimitDecorator implements Limit {

    private static final Logger LOG = LoggerFactory.getLogger(TracingLimitDecorator.class);

    private final Limit delegate;

    public static TracingLimitDecorator wrap(Limit delegate) {
        return new TracingLimitDecorator(delegate);
    }

    public TracingLimitDecorator(Limit delegate) {
        this.delegate = delegate;
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
        LOG.debug("maxInflight={} minRtt={} ms",
                inflight,
                TimeUnit.NANOSECONDS.toMicros(rtt)/1000.0);
        delegate.onSample(startTime, rtt, inflight, didDrop);
    }


}
