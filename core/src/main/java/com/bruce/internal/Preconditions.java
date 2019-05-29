package com.bruce.internal;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 21:07
 * @Version 1.0
 */
public final class Preconditions {

    public static void checkArgument(boolean expression, Object errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
    }

    public static void checkState(boolean expression, Object errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
    }

    private Preconditions() {}
}
