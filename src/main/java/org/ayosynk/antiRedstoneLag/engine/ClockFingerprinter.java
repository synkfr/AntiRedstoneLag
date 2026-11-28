package org.ayosynk.antiRedstoneLag.engine;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-pattern pulse fingerprinter distinguishing steady and alternating clocks from irregular bursts.
 */
public class ClockFingerprinter {
    private static final long PULSE_RETENTION_MS = 60_000L;

    private static final class PulseRing {
        final long[] intervals;
        long lastPulseMs;
        int count;
        int writeIdx;

        PulseRing(int capacity) {
            this.intervals = new long[capacity];
        }

        void record(long nowMs) {
            if (lastPulseMs > 0) {
                long delta = nowMs - lastPulseMs;
                intervals[writeIdx] = delta;
                writeIdx = (writeIdx + 1) % intervals.length;
                if (count < intervals.length) {
                    count++;
                }
            }
            lastPulseMs = nowMs;
        }

        boolean isPeriodicClock(long maxIntervalMs, long toleranceMs) {
            if (count < 3) {
                return false;
            }

            // Examine recent intervals from newest back to oldest.
            int sampleCount = Math.min(count, intervals.length);
            long[] recent = new long[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                int idx = (writeIdx - 1 - i + intervals.length) % intervals.length;
                recent[i] = intervals[idx];
            }

            long sum = 0L;
            for (long delta : recent) {
                if (delta > maxIntervalMs) {
                    return false;
                }
                sum += delta;
            }
            double avg = (double) sum / sampleCount;
            if (avg > maxIntervalMs) {
                return false;
            }

            // Period-1 steady clock check (e.g., 1t / 2t / 4t clocks or 0-tick spam).
            int steadyMatches = 0;
            for (int i = 1; i < sampleCount; i++) {
                if (Math.abs(recent[i] - recent[i - 1]) <= toleranceMs) {
                    steadyMatches++;
                }
            }
            int requiredSteady = sampleCount >= 5 ? sampleCount - 2 : sampleCount - 1;
            if (steadyMatches >= requiredSteady && sampleCount >= 3) {
                return true;
            }

            // Period-2 alternating clock check (e.g., asymmetric comparator/repeater clocks).
            if (sampleCount >= 4) {
                int altMatches = 0;
                for (int i = 2; i < sampleCount; i++) {
                    if (Math.abs(recent[i] - recent[i - 2]) <= toleranceMs) {
                        altMatches++;
                    }
                }
                int requiredAlt = sampleCount >= 6 ? sampleCount - 3 : sampleCount - 2;
                return altMatches >= requiredAlt;
            }
            return false;
        }
    }

    private final Map<UUID, Long2ObjectOpenHashMap<PulseRing>> history = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public boolean recordAndCheckPeriodic(UUID worldId, long blockKey, long nowMs,
                                            int historySize, long maxIntervalMs, long toleranceMs) {
        int cap = Math.max(4, Math.min(32, historySize));
        synchronized (lock) {
            Long2ObjectOpenHashMap<PulseRing> worldMap = history.computeIfAbsent(worldId, k -> new Long2ObjectOpenHashMap<>());
            PulseRing ring = worldMap.get(blockKey);
            if (ring == null || ring.intervals.length != cap) {
                ring = new PulseRing(cap);
                worldMap.put(blockKey, ring);
            }
            ring.record(nowMs);
            return ring.isPeriodicClock(maxIntervalMs, toleranceMs);
        }
    }

    public void pruneExpired(long nowMs) {
        long cutoff = nowMs - PULSE_RETENTION_MS;
        synchronized (lock) {
            for (Long2ObjectOpenHashMap<PulseRing> worldMap : history.values()) {
                Iterator<PulseRing> it = worldMap.values().iterator();
                while (it.hasNext()) {
                    if (it.next().lastPulseMs < cutoff) {
                        it.remove();
                    }
                }
            }
        }
    }

    public void clearBlock(UUID worldId, long blockKey) {
        synchronized (lock) {
            Long2ObjectOpenHashMap<PulseRing> worldMap = history.get(worldId);
            if (worldMap != null) {
                worldMap.remove(blockKey);
            }
        }
    }

    public void clearAll() {
        synchronized (lock) {
            history.clear();
        }
    }
}
