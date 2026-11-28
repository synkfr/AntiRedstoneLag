package org.ayosynk.antiRedstoneLag;

import org.ayosynk.antiRedstoneLag.engine.ClockFingerprinter;
import org.ayosynk.antiRedstoneLag.engine.RedstoneEngine;
import org.ayosynk.antiRedstoneLag.util.BlockKeyCodec;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedstoneEngineTests {

    @Test
    void fingerprinterDetectsSteadyAndAlternatingClocksWhileIgnoringBursts() {
        ClockFingerprinter fp = new ClockFingerprinter();
        UUID worldId = UUID.randomUUID();

        // Steady 100ms clock
        long steadyKey = BlockKeyCodec.packBlock(10, 64, 10);
        long t = 1_000L;
        assertFalse(fp.recordAndCheckPeriodic(worldId, steadyKey, t, 8, 500L, 45L));
        assertFalse(fp.recordAndCheckPeriodic(worldId, steadyKey, t + 100, 8, 500L, 45L));
        assertFalse(fp.recordAndCheckPeriodic(worldId, steadyKey, t + 200, 8, 500L, 45L));
        assertTrue(fp.recordAndCheckPeriodic(worldId, steadyKey, t + 300, 8, 500L, 45L));

        // Alternating 100ms / 250ms clock (period-2)
        long altKey = BlockKeyCodec.packBlock(20, 64, 20);
        long[] altTimestamps = {1000L, 1100L, 1350L, 1450L, 1700L};
        boolean detectedAlt = false;
        for (long ts : altTimestamps) {
            detectedAlt = fp.recordAndCheckPeriodic(worldId, altKey, ts, 8, 500L, 45L);
        }
        assertTrue(detectedAlt);

        // Irregular sorting burst should not classify as a periodic clock
        long burstKey = BlockKeyCodec.packBlock(30, 64, 30);
        long[] burstTimestamps = {1000L, 1020L, 1210L, 1235L, 1520L};
        boolean detectedBurst = false;
        for (long ts : burstTimestamps) {
            detectedBurst = fp.recordAndCheckPeriodic(worldId, burstKey, ts, 8, 500L, 45L);
        }
        assertFalse(detectedBurst);
    }

    @Test
    void sustainedGuardIgnoresSingleIntervalFarmBurst() {
        // One-second vanilla crop/sorter pulse (no activity in previous interval) stays unflagged.
        assertFalse(RedstoneEngine.isSustainedAcrossIntervals(850, 35, 0, 0, 800, 30));
        // Two consecutive elevated intervals trip protection.
        assertTrue(RedstoneEngine.isSustainedAcrossIntervals(850, 35, 500, 20, 800, 30));
        // Extreme single-interval lag bomb still trips immediately.
        assertTrue(RedstoneEngine.isSustainedAcrossIntervals(1600, 35, 0, 0, 800, 30));
        assertTrue(RedstoneEngine.isSustainedAcrossIntervals(850, 90, 0, 0, 800, 30));
    }
}
