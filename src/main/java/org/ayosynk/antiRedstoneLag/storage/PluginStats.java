package org.ayosynk.antiRedstoneLag.storage;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe persistent runtime statistics.
 */
public class PluginStats {
    public static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private final AtomicInteger totalRemovals = new AtomicInteger(0);
    private final AtomicInteger removalsToday = new AtomicInteger(0);
    private final AtomicInteger totalFreezes = new AtomicInteger(0);
    private final AtomicInteger totalLockdowns = new AtomicInteger(0);
    private final AtomicLong totalEntitiesCleared = new AtomicLong(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());

    public int getTotalRemovals() {
        return totalRemovals.get();
    }

    public void setTotalRemovals(int value) {
        totalRemovals.set(Math.max(0, value));
    }

    public int incrementTotalRemovals() {
        return totalRemovals.incrementAndGet();
    }

    public int getRemovalsToday() {
        return removalsToday.get();
    }

    public void setRemovalsToday(int value) {
        removalsToday.set(Math.max(0, value));
    }

    public int incrementRemovalsToday() {
        return removalsToday.incrementAndGet();
    }

    public int getTotalFreezes() {
        return totalFreezes.get();
    }

    public void setTotalFreezes(int value) {
        totalFreezes.set(Math.max(0, value));
    }

    public int incrementTotalFreezes() {
        return totalFreezes.incrementAndGet();
    }

    public int getTotalLockdowns() {
        return totalLockdowns.get();
    }

    public void setTotalLockdowns(int value) {
        totalLockdowns.set(Math.max(0, value));
    }

    public int incrementTotalLockdowns() {
        return totalLockdowns.incrementAndGet();
    }

    public long getTotalEntitiesCleared() {
        return totalEntitiesCleared.get();
    }

    public void setTotalEntitiesCleared(long value) {
        totalEntitiesCleared.set(Math.max(0L, value));
    }

    public long addEntitiesCleared(int delta) {
        return delta > 0 ? totalEntitiesCleared.addAndGet(delta) : totalEntitiesCleared.get();
    }

    public long getLastResetTime() {
        return lastResetTime.get();
    }

    public void setLastResetTime(long value) {
        lastResetTime.set(value > 0 ? value : System.currentTimeMillis());
    }

    public boolean rolloverIfNeeded(long now) {
        long prev = lastResetTime.get();
        if (now - prev > DAY_MS && lastResetTime.compareAndSet(prev, now)) {
            removalsToday.set(0);
            return true;
        }
        return false;
    }

    public StatsSnapshot snapshot() {
        return new StatsSnapshot(
                totalRemovals.get(),
                removalsToday.get(),
                totalFreezes.get(),
                totalLockdowns.get(),
                totalEntitiesCleared.get(),
                lastResetTime.get()
        );
    }

    public record StatsSnapshot(
            int totalRemovals,
            int removalsToday,
            int totalFreezes,
            int totalLockdowns,
            long totalEntitiesCleared,
            long lastResetTime
    ) {
    }
}
