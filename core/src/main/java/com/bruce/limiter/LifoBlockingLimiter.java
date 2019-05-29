package com.bruce.limiter;

import com.bruce.Limiter;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * @Author: Bruce
 * @Date: 2019/5/28 23:15
 * @Version 1.0
 */
public class LifoBlockingLimiter<ContextT> implements Limiter<ContextT> {

    public static class Builder<ContextT> {
        private final Limiter<ContextT> delegate;
        private int maxBacklogSize = 100;
        private Function<ContextT, Long> maxBacklogTimeoutMillis = contextT -> 1000L;

        private Builder(Limiter<ContextT> delegate) {
            this.delegate = delegate;
        }

        public Builder<ContextT> maxBacklogSize(int size) {
            this.maxBacklogSize = size;
            return this;
        }

        public Builder<ContextT> backlogTimeout(long timeout, TimeUnit units) {
            return backlogTimeoutMillis(units.toMillis(timeout));
        }

        public Builder<ContextT> backlogTimeoutMillis(long timeout) {
            this.maxBacklogTimeoutMillis = contextT -> timeout;
            return this;
        }

        public Builder<ContextT> backlogTimeout(Function<ContextT, Long> mapper, TimeUnit units) {
            this.maxBacklogTimeoutMillis = context -> units.toMillis(mapper.apply(context));
            return this;
        }

        public LifoBlockingLimiter<ContextT> build() {
            return new LifoBlockingLimiter<ContextT>(this);
        }

    }

    public static <ContextT> Builder<ContextT> newBuilder(Limiter<ContextT> delegate) {
        return new Builder<ContextT>(delegate);
    }

    private final Limiter<ContextT> delegate;

    private static class ListenerHolder<ContextT> {
        private volatile Optional<Listener> listener;
        private final CountDownLatch latch = new CountDownLatch(1);
        private ContextT context;

        public ListenerHolder(ContextT context) {
            this.context = context;
        }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        public void set(Optional<Listener> listener) {
            this.listener = listener;
            latch.countDown();
        }

    }

    private final Deque<ListenerHolder<ContextT>> backlog = new LinkedList<>();

    private final AtomicInteger backlogCounter = new AtomicInteger();

    private final int backlogSize;

    private final Function<ContextT, Long> backlogTimeoutMillis;

    private final Object lock = new Object();

    private LifoBlockingLimiter(Builder<ContextT> builder) {
        this.delegate = builder.delegate;
        this.backlogSize = builder.maxBacklogSize;
        backlogTimeoutMillis = builder.maxBacklogTimeoutMillis;
    }

    private Optional<Listener> tryAcquire(ContextT context) {
        final Optional<Listener> listener = delegate.acquire(context);
        if (listener.isPresent()) {
            return listener;
        }

        if (backlogCounter.get() > this.backlogSize) {
            return Optional.empty();
        }

        backlogCounter.incrementAndGet();
        final ListenerHolder<ContextT> event = new ListenerHolder<>(context);

        try {
            synchronized (lock) {
                backlog.addFirst(event);
            }

            if (!event.await(backlogTimeoutMillis.apply(context), TimeUnit.MILLISECONDS)) {
                synchronized (lock) {
                    backlog.removeLastOccurrence(event);
                }
                return Optional.empty();
            }
            return event.listener;
        } catch (InterruptedException e) {
            synchronized (lock) {
                backlog.removeFirstOccurrence(event);
            }
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            backlogCounter.decrementAndGet();
        }
    }

    private void unlock() {
        synchronized (lock) {
            if (!backlog.isEmpty()) {
                final ListenerHolder<ContextT> event = backlog.peekFirst();
                final Optional<Listener> listener = delegate.acquire(event.context);
                if (listener.isPresent()) {
                    backlog.removeFirst();
                    event.set(listener);
                } else {
                    return;
                }
            }
        }
    }

    @Override
    public Optional<Listener> acquire(ContextT context) {
        return tryAcquire(context).map(delegate -> {
            return new Listener() {
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
            };
        });
    }

    @Override
    public String toString() {
        return "BlockingLimiter [" + delegate + "]";
    }
}
