package com.bruce;

import java.util.function.Supplier;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 19:45
 * @Version 1.0
 */
public interface MetricRegistry {

    interface SampleListener {
        void addSample(Number value);
    }

    SampleListener registerDistribution(String id, String... tagNameValuePairs);

    void registerGauge(String id, Supplier<Number> supplier, String... tagNameValuePairs);


}
