package com.bruce.executors;

import com.bruce.Limiter;
import com.bruce.limiter.BlockingLimiter;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 18:03
 * @Version 1.0
 */
public final class BlockingAdaptiveExecutor implements Executor {

    private final Limiter<Void> limiter;

    private final Executor executor;

    public BlockingAdaptiveExecutor(Limiter<Void> limiter) {
        this(limiter, Executors.newCachedThreadPool());
    }

    public BlockingAdaptiveExecutor(Limiter<Void> limiter, Executor executor) {
        this.limiter = BlockingLimiter.wrap(limiter);
        this.executor = executor;
    }

    @Override
    public void execute(Runnable command) {
        Limiter.Listener token = limiter.acquire(null).orElseThrow(() -> new RejectedExecutionException());
        executor.execute(() -> {
            try {
                command.run();
                token.onSuccess();
            } catch (RejectedExecutionException e) {
                token.onDropped();
            } catch (Exception e) {
                token.onIgnore();
            }
        });
    }
}
