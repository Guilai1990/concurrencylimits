package com.bruce;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 19:44
 * @Version 1.0
 */
public interface LimiterRegistry<ContextT> {

    Limiter<ContextT> get(String key);

    static <ContextT> LimiterRegistry<ContextT> single(Limiter<ContextT> limiter) {
        return key -> limiter;
    }
}
