package com.bruce.internal;

import com.bruce.MetricRegistry;

import java.util.function.Supplier;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 21:20
 * @Version 1.0
 */
public class EmptyMetricRegistry implements MetricRegistry {

    public static final EmptyMetricRegistry INSTANCE = new EmptyMetricRegistry();

    private EmptyMetricRegistry() {}

    @Override
    public SampleListener registerDistribution(String id, String... tagNameValuePairs) {
        return value -> {};
    }

    @Override
    public void registerGauge(String id, Supplier<Number> supplier, String... tagNameValuePairs) {

    }
}
