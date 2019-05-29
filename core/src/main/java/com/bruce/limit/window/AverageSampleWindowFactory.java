package com.bruce.limit.window;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 16:43
 * @Version 1.0
 */
public class AverageSampleWindowFactory implements SampleWindowFactory {

    private static final AverageSampleWindowFactory INSTANCE = new AverageSampleWindowFactory();

    private AverageSampleWindowFactory() {}

    public static AverageSampleWindowFactory create() {
        return INSTANCE;
    }

    @Override
    public SampleWindow newInstance() {
        return new ImmutableAverageSampleWindow();
    }
}
