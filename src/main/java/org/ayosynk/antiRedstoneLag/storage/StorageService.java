package org.ayosynk.antiRedstoneLag.storage;

import org.ayosynk.antiRedstoneLag.diagnostics.SnapshotRecord;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence contract for block owners, forensic snapshots, and statistics.
 */
public interface StorageService {

    String backendName();

    void initialize();

    void close();

    void saveBlockOwnerAsync(UUID worldId, long chunkKey, long blockKey, UUID ownerUuid);

    void removeBlockOwnerAsync(UUID worldId, long chunkKey, long blockKey);

    void removeChunkOwnersAsync(UUID worldId, long chunkKey);

    Map<Long, UUID> loadChunkOwners(UUID worldId, long chunkKey);

    CompletableFuture<Map<Long, UUID>> loadChunkOwnersAsync(UUID worldId, long chunkKey);

    void saveSnapshotAsync(SnapshotRecord snapshot);

    void deleteSnapshotAsync(String id);

    void clearSnapshotsAsync();

    List<SnapshotRecord> loadSnapshots(int limit);

    PluginStats loadStats();

    void saveStats(PluginStats stats);

    void saveStatsAsync(PluginStats stats);

    void pruneExpiredOwnersAsync(int retentionDays);
}
