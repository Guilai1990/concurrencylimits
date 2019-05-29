package com.bruce.limit.measurement;

import java.util.function.Function;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 21:30
 * @Version 1.0
 */
public interface Measurement {

    Number add(Number sample);

    Number get();

    void reset();

    void update(Function<Number, Number> operation);
}
