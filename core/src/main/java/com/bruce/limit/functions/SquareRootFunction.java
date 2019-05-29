package com.bruce.limit.functions;

import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 23:26
 * @Version 1.0
 */
public final class SquareRootFunction implements Function<Integer, Integer> {

    static final int[] lookup = new int[1000];

    static {
        IntStream.range(0, 1000).forEach(i -> lookup[i] = Math.max(1, (int)Math.sqrt(i)));
    }

    private static final SquareRootFunction INSTANCE = new SquareRootFunction();


    @Override
    public Integer apply(Integer t) {
        return t < 1000 ? lookup[t] : (int)Math.sqrt(t);
    }

  public static Function<Integer, Integer> create(int baseline) {
        return INSTANCE.andThen(t -> Math.max(baseline, t));
  }

}
