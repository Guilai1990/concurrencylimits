package com.bruce.limiter;

import com.bruce.Limiter;
import com.bruce.internal.Preconditions;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 18:09
 * @Version 1.0
 */
public class BlockingLimiter<ContextT> implements Limiter<ContextT> {

    public static final Duration MAX_TIMEOUT = Duration.ofHours(1);

    public static <ContextT> BlockingLimiter<ContextT> wrap(Limiter<ContextT> delegate) {
        return new BlockingLimiter<>(delegate, MAX_TIMEOUT);
    }

    public static <ContextT> BlockingLimiter<ContextT> wrap(Limiter<ContextT> delegate, Duration timeout) {
        Preconditions.checkArgument(timeout.compareTo(MAX_TIMEOUT) < 0, "Timeout cannot be greater than " + MAX_TIMEOUT);
        return new BlockingLimiter<>(delegate, timeout);
    }

    private final Limiter<ContextT> delegate;
    private final Duration timeout;

    private final Object lock = new Object();

    private BlockingLimiter(Limiter<ContextT> limiter, Duration timeout) {
        this.delegate = limiter;
        this.timeout = timeout;
    }

    private Optional<Listener> tryAcquire(ContextT context) {
        final Instant deadline = Instant.now().plus(timeout);
        synchronized (lock) {
            while (true) {
                long timeout = Duration.between(Instant.now(), deadline).toMillis();
                if (timeout <= 0) {
                    return Optional.empty();
                }

                final Optional<Listener> listener = delegate.acquire(context);
                if (listener.isPresent()) {
                    return listener;
                }

                try {
                    lock.wait(timeout);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }

    }

    private void unlock() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }


    @Override
    public Optional<Listener> acquire(ContextT context) {
        return tryAcquire(context).map(delegate -> new Listener() {
            @Override
            public void onSuccess() {
                delegate.onSuccess();
                unlock();
            }

            @Override
            public void onIgnore() {
                delegate.onIgnore();
                unlock();
            }

            @Override
            public void onDropped() {
                delegate.onDropped();
                unlock();
            }
        });
    }

    @Override
    public String toString() {
        return "BlockingLimiter [" + delegate + "]";
    }
}
