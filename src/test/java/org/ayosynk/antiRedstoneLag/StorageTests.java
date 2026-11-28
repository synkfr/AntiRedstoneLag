package org.ayosynk.antiRedstoneLag;

import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.ayosynk.antiRedstoneLag.diagnostics.SnapshotRecord;
import org.ayosynk.antiRedstoneLag.storage.JsonStorageService;
import org.ayosynk.antiRedstoneLag.storage.PluginStats;
import org.ayosynk.antiRedstoneLag.storage.SqliteStorageService;
import org.ayosynk.antiRedstoneLag.storage.StorageFactory;
import org.ayosynk.antiRedstoneLag.storage.StorageService;
import org.ayosynk.antiRedstoneLag.util.BlockKeyCodec;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTests {

    @Test
    void jsonStoragePersistsOwnersSnapshotsAndMigratesV1Stats(@TempDir Path tempDir) throws Exception {
        String legacyStats = """
                total-removals: 42
                removals-today: 7
                last-reset-time: %d
                """.formatted(System.currentTimeMillis());
        Files.writeString(tempDir.resolve("stats.yml"), legacyStats, StandardCharsets.UTF_8);

        Logger logger = Logger.getLogger("StorageTests");
        JsonStorageService storage = new JsonStorageService(tempDir.toFile(), logger);
        storage.initialize();
        verifyRoundTrip(storage, () -> {
            JsonStorageService r = new JsonStorageService(tempDir.toFile(), logger);
            r.initialize();
            return r;
        }, 42, 7);
    }

    @Test
    void sqliteStoragePersistsOwnersSnapshotsAndStatsWhenDriverPresent(@TempDir Path tempDir) {
        if (!SqliteStorageService.isDriverAvailable()) {
            return;
        }
        Logger logger = Logger.getLogger("SqliteStorageTests");
        StorageService s1 = StorageFactory.create(tempDir.toFile(), new PluginConfig.StorageConfig(), logger);
        assertEquals("SQLite", s1.backendName());
        verifyRoundTrip(s1, () -> StorageFactory.create(tempDir.toFile(), new PluginConfig.StorageConfig(), logger), 0, 0);
    }

    private static void verifyRoundTrip(StorageService storage, java.util.function.Supplier<StorageService> reloadSupplier,
                                        int expectedTotal, int expectedToday) {
        PluginStats stats = storage.loadStats();
        assertEquals(expectedTotal, stats.getTotalRemovals());
        assertEquals(expectedToday, stats.getRemovalsToday());
        stats.incrementTotalFreezes();
        stats.addEntitiesCleared(12);
        storage.saveStats(stats);

        UUID worldId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        long chunkKey = BlockKeyCodec.packChunk(3, -4);
        long blockKey = BlockKeyCodec.packBlock(50, 70, -60);

        storage.saveBlockOwnerAsync(worldId, chunkKey, blockKey, ownerId);
        SnapshotRecord snap = new SnapshotRecord(
                "SNP-1", System.currentTimeMillis(), "world", 50, 70, -60,
                Material.OBSERVER, 600, 30, ownerId, "Tester", "Clock test",
                Map.of(Material.OBSERVER, 2),
                List.of(new SnapshotRecord.BlockEntry(0, 0, 0, Material.OBSERVER, ownerId))
        );
        storage.saveSnapshotAsync(snap);
        storage.close();

        StorageService reloaded = reloadSupplier.get();
        try {
            PluginStats reloadedStats = reloaded.loadStats();
            assertEquals(1, reloadedStats.getTotalFreezes());
            assertEquals(12, reloadedStats.getTotalEntitiesCleared());

            Map<Long, UUID> loadedOwners = reloaded.loadChunkOwners(worldId, chunkKey);
            assertEquals(ownerId, loadedOwners.get(blockKey));

            List<SnapshotRecord> loadedSnaps = reloaded.loadSnapshots(10);
            assertEquals(1, loadedSnaps.size());
            assertEquals("SNP-1", loadedSnaps.get(0).id);
            assertEquals(Material.OBSERVER, loadedSnaps.get(0).triggerMaterial);
            assertTrue(loadedSnaps.get(0).getTotalComponents() == 2);
        } finally {
            reloaded.close();
        }
    }
}
