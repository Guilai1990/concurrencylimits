package com.bruce.limit.functions;

import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 15:10
 * @Version 1.0
 */
public class Log10RootFunction implements Function<Integer, Integer> {

    static final int[] lookup = new int[1000];

    static {
        IntStream.range(0, 1000).forEach(i -> lookup[i] = Math.max(1, (int)Math.log10(i)));
    }

    private static final Log10RootFunction INSTANCE = new Log10RootFunction();

    public static Function<Integer, Integer> create(int baseline) {
        return INSTANCE.andThen(t -> t+baseline);
    }

    @Override
    public Integer apply(Integer t) {
        return t < 1000 ? lookup[t] : (int)Math.log10(t);
    }


}
