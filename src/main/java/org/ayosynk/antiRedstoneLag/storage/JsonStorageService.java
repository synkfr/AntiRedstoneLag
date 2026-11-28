package org.ayosynk.antiRedstoneLag.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.ayosynk.antiRedstoneLag.diagnostics.SnapshotRecord;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Atomic Gson-backed persistent storage for owners, snapshots, and statistics.
 */
public class JsonStorageService implements StorageService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type OWNER_MAP_TYPE = new TypeToken<Map<String, Map<Long, OwnerRecord>>>() {
    }.getType();
    private static final Type SNAPSHOT_LIST_TYPE = new TypeToken<List<SerializedSnapshot>>() {
    }.getType();

    private final File dataFolder;
    private final File storageDir;
    private final File ownersFile;
    private final File snapshotsFile;
    private final File statsFile;
    private final File legacyStatsFile;
    private final Logger logger;

    private final Map<String, Map<Long, OwnerRecord>> owners = new ConcurrentHashMap<>();
    private final Map<String, SnapshotRecord> snapshots = new LinkedHashMap<>();
    private final Object snapshotLock = new Object();
    private final ExecutorService ioExecutor;

    private volatile boolean ownersDirty = false;
    private volatile boolean snapshotsDirty = false;

    private static class OwnerRecord {
        String ownerUuid;
        long updatedAt;

        OwnerRecord(String ownerUuid, long updatedAt) {
            this.ownerUuid = ownerUuid;
            this.updatedAt = updatedAt;
        }
    }

    private static class SerializedSnapshot {
        String id;
        long timestamp;
        String worldName;
        int x, y, z;
        String triggerMaterial;
        int chunkUpdates;
        int blockUpdates;
        String culpritUuid;
        String culpritName;
        String reason;
        Map<String, Integer> componentCounts;
        List<SerializedBlockEntry> blockEntries;
    }

    private static class SerializedBlockEntry {
        int relX, relY, relZ;
        String material;
        String ownerUuid;
    }

    public JsonStorageService(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.storageDir = new File(dataFolder, "data");
        this.ownersFile = new File(storageDir, "owners.json");
        this.snapshotsFile = new File(storageDir, "snapshots.json");
        this.statsFile = new File(storageDir, "stats.json");
        this.legacyStatsFile = new File(dataFolder, "stats.yml");
        this.logger = logger;
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AntiRedstoneLag-JsonStorage");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String backendName() {
        return "JSON";
    }

    @Override
    public void initialize() {
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        loadOwnersFromDisk();
        loadSnapshotsFromDisk();
    }

    @Override
    public void close() {
        try {
            ioExecutor.submit(() -> {
                if (ownersDirty) writeOwnersToDisk();
                if (snapshotsDirty) writeSnapshotsToDisk();
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void saveBlockOwnerAsync(UUID worldId, long chunkKey, long blockKey, UUID ownerUuid) {
        if (worldId == null || ownerUuid == null) return;
        String scope = worldChunkKey(worldId, chunkKey);
        owners.computeIfAbsent(scope, k -> new ConcurrentHashMap<>())
                .put(blockKey, new OwnerRecord(ownerUuid.toString(), System.currentTimeMillis()));
        ownersDirty = true;
        ioExecutor.execute(this::flushOwnersIfDirty);
    }

    @Override
    public void removeBlockOwnerAsync(UUID worldId, long chunkKey, long blockKey) {
        if (worldId == null) return;
        String scope = worldChunkKey(worldId, chunkKey);
        Map<Long, OwnerRecord> map = owners.get(scope);
        if (map != null && map.remove(blockKey) != null) {
            if (map.isEmpty()) {
                owners.remove(scope, map);
            }
            ownersDirty = true;
            ioExecutor.execute(this::flushOwnersIfDirty);
        }
    }

    @Override
    public void removeChunkOwnersAsync(UUID worldId, long chunkKey) {
        if (worldId == null) return;
        if (owners.remove(worldChunkKey(worldId, chunkKey)) != null) {
            ownersDirty = true;
            ioExecutor.execute(this::flushOwnersIfDirty);
        }
    }

    @Override
    public Map<Long, UUID> loadChunkOwners(UUID worldId, long chunkKey) {
        if (worldId == null) return Map.of();
        Map<Long, OwnerRecord> map = owners.get(worldChunkKey(worldId, chunkKey));
        if (map == null || map.isEmpty()) return Map.of();
        Map<Long, UUID> result = new HashMap<>(map.size());
        for (Map.Entry<Long, OwnerRecord> e : map.entrySet()) {
            try {
                result.put(e.getKey(), UUID.fromString(e.getValue().ownerUuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    @Override
    public CompletableFuture<Map<Long, UUID>> loadChunkOwnersAsync(UUID worldId, long chunkKey) {
        return CompletableFuture.supplyAsync(() -> loadChunkOwners(worldId, chunkKey), ioExecutor);
    }

    @Override
    public void saveSnapshotAsync(SnapshotRecord snapshot) {
        if (snapshot == null) return;
        synchronized (snapshotLock) {
            snapshots.put(snapshot.id, snapshot);
            snapshotsDirty = true;
        }
        ioExecutor.execute(this::flushSnapshotsIfDirty);
    }

    @Override
    public void deleteSnapshotAsync(String id) {
        if (id == null) return;
        synchronized (snapshotLock) {
            if (snapshots.remove(id.toUpperCase()) != null) {
                snapshotsDirty = true;
                ioExecutor.execute(this::flushSnapshotsIfDirty);
            }
        }
    }

    @Override
    public void clearSnapshotsAsync() {
        synchronized (snapshotLock) {
            snapshots.clear();
            snapshotsDirty = true;
        }
        ioExecutor.execute(this::flushSnapshotsIfDirty);
    }

    @Override
    public List<SnapshotRecord> loadSnapshots(int limit) {
        synchronized (snapshotLock) {
            List<SnapshotRecord> list = new ArrayList<>(snapshots.values());
            list.sort(Comparator.comparingLong((SnapshotRecord s) -> s.timestamp).reversed());
            if (limit > 0 && list.size() > limit) {
                return new ArrayList<>(list.subList(0, limit));
            }
            return list;
        }
    }

    @Override
    public PluginStats loadStats() {
        PluginStats stats = new PluginStats();
        if (statsFile.exists()) {
            try {
                String json = Files.readString(statsFile.toPath(), StandardCharsets.UTF_8);
                PluginStats.StatsSnapshot snap = GSON.fromJson(json, PluginStats.StatsSnapshot.class);
                if (snap != null) {
                    stats.setTotalRemovals(snap.totalRemovals());
                    stats.setRemovalsToday(snap.removalsToday());
                    stats.setTotalFreezes(snap.totalFreezes());
                    stats.setTotalLockdowns(snap.totalLockdowns());
                    stats.setTotalEntitiesCleared(snap.totalEntitiesCleared());
                    stats.setLastResetTime(snap.lastResetTime());
                }
            } catch (Exception e) {
                logWarn("Failed to read stats.json: " + e.getMessage());
            }
        } else if (legacyStatsFile.exists()) {
            migrateLegacyStatsYml(stats);
            saveStats(stats);
        }
        stats.rolloverIfNeeded(System.currentTimeMillis());
        return stats;
    }

    private void migrateLegacyStatsYml(PluginStats stats) {
        try {
            YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyStatsFile);
            stats.setTotalRemovals(legacy.getInt("total-removals", 0));
            stats.setRemovalsToday(legacy.getInt("removals-today", 0));
            stats.setLastResetTime(legacy.getLong("last-reset-time", System.currentTimeMillis()));
            if (logger != null) {
                logger.info("Migrated v1 stats.yml into v2 persistent stats.");
            }
        } catch (Exception e) {
            logWarn("Failed to migrate v1 stats.yml: " + e.getMessage());
        }
    }

    @Override
    public void saveStats(PluginStats stats) {
        if (stats == null) return;
        if (!storageDir.exists()) storageDir.mkdirs();
        try {
            atomicWrite(statsFile, GSON.toJson(stats.snapshot()));
        } catch (IOException e) {
            logWarn("Failed to save stats.json: " + e.getMessage());
        }
    }

    @Override
    public void saveStatsAsync(PluginStats stats) {
        if (stats == null) return;
        PluginStats.StatsSnapshot snap = stats.snapshot();
        ioExecutor.execute(() -> {
            try {
                if (!storageDir.exists()) storageDir.mkdirs();
                atomicWrite(statsFile, GSON.toJson(snap));
            } catch (IOException e) {
                logWarn("Failed to save stats.json async: " + e.getMessage());
            }
        });
    }

    @Override
    public void pruneExpiredOwnersAsync(int retentionDays) {
        long cutoff = System.currentTimeMillis() - (Math.max(1L, retentionDays) * PluginStats.DAY_MS);
        ioExecutor.execute(() -> {
            boolean removedAny = false;
            for (var entry : owners.entrySet()) {
                Map<Long, OwnerRecord> map = entry.getValue();
                if (map.entrySet().removeIf(e -> e.getValue().updatedAt < cutoff)) {
                    removedAny = true;
                }
                if (map.isEmpty()) {
                    owners.remove(entry.getKey(), map);
                }
            }
            if (removedAny) {
                ownersDirty = true;
                flushOwnersIfDirty();
            }
        });
    }

    private void flushOwnersIfDirty() {
        if (ownersDirty) {
            ownersDirty = false;
            writeOwnersToDisk();
        }
    }

    private void flushSnapshotsIfDirty() {
        if (snapshotsDirty) {
            snapshotsDirty = false;
            writeSnapshotsToDisk();
        }
    }

    private void loadOwnersFromDisk() {
        if (!ownersFile.exists()) return;
        try {
            String raw = Files.readString(ownersFile.toPath(), StandardCharsets.UTF_8);
            Map<String, Map<Long, OwnerRecord>> parsed = GSON.fromJson(raw, OWNER_MAP_TYPE);
            if (parsed != null) {
                for (var entry : parsed.entrySet()) {
                    owners.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
                }
            }
        } catch (Exception e) {
            logWarn("Failed to load owners.json: " + e.getMessage());
        }
    }

    private void writeOwnersToDisk() {
        try {
            if (!storageDir.exists()) storageDir.mkdirs();
            Map<String, Map<Long, OwnerRecord>> copy = new HashMap<>();
            for (var entry : owners.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }
            }
            atomicWrite(ownersFile, GSON.toJson(copy));
        } catch (IOException e) {
            logWarn("Failed to write owners.json: " + e.getMessage());
        }
    }

    private void loadSnapshotsFromDisk() {
        if (!snapshotsFile.exists()) return;
        try {
            String raw = Files.readString(snapshotsFile.toPath(), StandardCharsets.UTF_8);
            List<SerializedSnapshot> parsed = GSON.fromJson(raw, SNAPSHOT_LIST_TYPE);
            if (parsed == null) return;
            synchronized (snapshotLock) {
                snapshots.clear();
                for (SerializedSnapshot s : parsed) {
                    SnapshotRecord rec = deserializeSnapshot(s);
                    if (rec != null) {
                        snapshots.put(rec.id, rec);
                    }
                }
            }
        } catch (Exception e) {
            logWarn("Failed to load snapshots.json: " + e.getMessage());
        }
    }

    private void writeSnapshotsToDisk() {
        List<SerializedSnapshot> serialized = new ArrayList<>();
        synchronized (snapshotLock) {
            for (SnapshotRecord rec : snapshots.values()) {
                serialized.add(serializeSnapshot(rec));
            }
        }
        try {
            if (!storageDir.exists()) storageDir.mkdirs();
            atomicWrite(snapshotsFile, GSON.toJson(serialized));
        } catch (IOException e) {
            logWarn("Failed to write snapshots.json: " + e.getMessage());
        }
    }

    private static SerializedSnapshot serializeSnapshot(SnapshotRecord rec) {
        SerializedSnapshot s = new SerializedSnapshot();
        s.id = rec.id;
        s.timestamp = rec.timestamp;
        s.worldName = rec.worldName;
        s.x = rec.x;
        s.y = rec.y;
        s.z = rec.z;
        s.triggerMaterial = rec.triggerMaterial.name();
        s.chunkUpdates = rec.chunkUpdates;
        s.blockUpdates = rec.blockUpdates;
        s.culpritUuid = rec.culpritUuid != null ? rec.culpritUuid.toString() : null;
        s.culpritName = rec.culpritName;
        s.reason = rec.reason;
        s.componentCounts = new LinkedHashMap<>();
        for (var entry : rec.componentCounts.entrySet()) {
            s.componentCounts.put(entry.getKey().name(), entry.getValue());
        }
        s.blockEntries = new ArrayList<>(rec.blockEntries.size());
        for (SnapshotRecord.BlockEntry be : rec.blockEntries) {
            SerializedBlockEntry sbe = new SerializedBlockEntry();
            sbe.relX = be.relX;
            sbe.relY = be.relY;
            sbe.relZ = be.relZ;
            sbe.material = be.material.name();
            sbe.ownerUuid = be.ownerUuid != null ? be.ownerUuid.toString() : null;
            s.blockEntries.add(sbe);
        }
        return s;
    }

    private static SnapshotRecord deserializeSnapshot(SerializedSnapshot s) {
        if (s == null || s.id == null) return null;
        Material trigger = Material.matchMaterial(s.triggerMaterial != null ? s.triggerMaterial : "REDSTONE_WIRE");
        if (trigger == null) trigger = Material.REDSTONE_WIRE;

        UUID culprit = parseUuid(s.culpritUuid);
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        if (s.componentCounts != null) {
            for (var e : s.componentCounts.entrySet()) {
                Material m = Material.matchMaterial(e.getKey());
                if (m != null) counts.put(m, e.getValue());
            }
        }
        List<SnapshotRecord.BlockEntry> entries = new ArrayList<>();
        if (s.blockEntries != null) {
            for (SerializedBlockEntry be : s.blockEntries) {
                Material m = Material.matchMaterial(be.material);
                if (m != null) {
                    entries.add(new SnapshotRecord.BlockEntry(be.relX, be.relY, be.relZ, m, parseUuid(be.ownerUuid)));
                }
            }
        }
        return new SnapshotRecord(
                s.id, s.timestamp, s.worldName, s.x, s.y, s.z,
                trigger, s.chunkUpdates, s.blockUpdates,
                culprit, s.culpritName != null ? s.culpritName : "Unknown",
                s.reason != null ? s.reason : "Unknown", counts, entries
        );
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String worldChunkKey(UUID worldId, long chunkKey) {
        return worldId + ":" + chunkKey;
    }

    private static void atomicWrite(File target, String content) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void logWarn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
