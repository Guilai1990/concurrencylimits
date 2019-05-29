package com.bruce.limiter;

import com.bruce.Limiter;
import com.bruce.MetricIds;
import com.bruce.MetricRegistry;
import com.bruce.internal.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * @Author: Bruce
 * @Date: 2019/5/29 19:39
 * @Version 1.0
 */
public class AbstractPartitionedLimiter<ContextT> extends AbstractLimiter<ContextT> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractLimiter.class);
    private static final String PARTITION_TAG_NAME = "partition";

    public abstract static class Builder<BuilderT extends AbstractLimiter.Builder<BuilderT>, ContextT> extends AbstractLimiter.Builder<BuilderT> {
        private List<Function<ContextT, String>> partitionResolvers = new ArrayList<>();
        private final Map<String, Partition> partitions = new LinkedHashMap<>();
        private int maxDelayedThreads = 100;

        public BuilderT partitionResolver(Function<ContextT, String> contextToPartition) {
            this.partitionResolvers.add(contextToPartition);
            return self();
        }

        public BuilderT partition(String name, double percent) {
            Preconditions.checkArgument(name != null, "Partition name may not be null");
            Preconditions.checkArgument(percent >= 0.0 && percent <= 1.0, "Partition percentage must be in the range [0.0, 1.0]");
            partitions.computeIfAbsent(name, Partition::new).setPercent(percent);
            return self();
        }

        public BuilderT partitionRejectDelay(String name, long duration, TimeUnit units) {
            partitions.computeIfAbsent(name, Partition::new).setBackofMillis(units.toMillis(duration));
            return self();
        }

        public BuilderT maxDelayedThreads(int maxDelayedThreads) {
            this.maxDelayedThreads = maxDelayedThreads;
            return self();
        }

        protected boolean hasPartions() {
            return !partitions.isEmpty();
        }

        public Limiter<ContextT> build() {
            return (this.hasPartions() && !partitionResolvers.isEmpty()) ? new AbstractPartitionedLimiter<ContextT>(this) {}
            : new SimpleLimiter<ContextT>(this);
        }
    }

    static class Partition {
        private final String name;
        private double percent = 0.0;
        private int limit = 0;
        private int busy = 0;
        private long backofMillis = 0;
        private MetricRegistry.SampleListener inflightDistribution;

        Partition(String name) {
            this.name = name;
        }

        Partition setPercent(double percent) {
            this.percent = percent;
            return this;
        }

        Partition setBackofMillis(long backofMillis) {
            this.backofMillis = backofMillis;
            return this;
        }

        void updateLimit(int totalLimit) {
            this.limit = (int)Math.max(1, Math.ceil(totalLimit * percent));
        }

        boolean isLimitExceeded() {
            return busy >= limit;
        }

        void acquire() {
            busy++;
            inflightDistribution.addSample(busy);
        }

        void release() {
            busy--;
        }

        int getLimit() {
            return limit;
        }

        public int getInflight() {
            return busy;
        }

        double getPercent() {
            return percent;
        }

        void createMetrics(MetricRegistry registry) {
            this.inflightDistribution = registry.registerDistribution(MetricIds.INFLIGHT_NAME, PARTITION_TAG_NAME, name);
            registry.registerGauge(MetricIds.PARTITION_LIMIT_NAME, this::getLimit, PARTITION_TAG_NAME, name);
        }

        @Override
        public String toString() {
            return "Partition [pct=" + percent + ", limit" + limit + ", busy=" + busy + "]";
        }
    }

    private final Map<String, Partition> partitions;
    private final Partition unknownPartition;
    private final List<Function<ContextT, String>> partitionResolvers;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger delayThreads = new AtomicInteger();
    private final int maxDelayedThreads;

    public AbstractPartitionedLimiter(Builder<?, ContextT> builder) {
        super(builder);

        Preconditions.checkArgument(!builder.partitions.isEmpty(), "No partition specified");
        Preconditions.checkArgument(builder.partitions.values().stream().map(Partition::getPercent).reduce(0.0, Double::sum) <= 1.0,
        "Sum of percentages must be <= 1.0");

        this.partitions = new HashMap<>(builder.partitions);
        this.partitions.forEach((name, partition) -> partition.createMetrics(builder.registry));

        this.unknownPartition = new Partition("unknown");
        this.unknownPartition.createMetrics(builder.registry);

        this.partitionResolvers = builder.partitionResolvers;
        this.maxDelayedThreads = builder.maxDelayedThreads;

        onNewLimit(getLimit());
    }

    private Partition resolvePartition(ContextT context) {
        for (Function<ContextT, String> resolver : this.partitionResolvers) {
            String name = resolver.apply(context);
            if (name != null) {
                Partition partition = partitions.get(name);
                if (partition != null) {
                    return partition;
                }
            }
        }
        return unknownPartition;
    }



    @Override
    public Optional<Listener> acquire(ContextT context) {
        final Partition partition = resolvePartition(context);

        try {
            lock.lock();
            if (getInflight() >= getLimit() && partition.isLimitExceeded()) {
                lock.unlock();
                if (partition.backofMillis >0 && delayThreads.get() < maxDelayedThreads) {
                    try {
                        delayThreads.incrementAndGet();
                        TimeUnit.MILLISECONDS.sleep(partition.backofMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        delayThreads.decrementAndGet();
                    }
                }
                return Optional.empty();
            }

            partition.acquire();
            final Listener listener = createListener();
            return Optional.of(new Listener() {
                @Override
                public void onSuccess() {
                    listener.onSuccess();
                    releasePartition(partition);
                }

                @Override
                public void onIgnore() {
                    listener.onIgnore();
                    releasePartition(partition);
                }

                @Override
                public void onDropped() {
                    listener.onDropped();
                    releasePartition(partition);
                }
            });
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void releasePartition(Partition partition) {
        try {
            lock.lock();
            partition.release();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onNewLimit(int newLimit) {
        super.onNewLimit(newLimit);
        partitions.forEach((name, partition) -> partition.updateLimit(newLimit));
    }

    Partition getPartition(String name) {
        return partitions.get(name);
    }
}
