package org.ayosynk.antiRedstoneLag.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.ayosynk.antiRedstoneLag.diagnostics.SnapshotRecord;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * SQLite-backed persistent storage using WAL mode and a dedicated single-writer thread.
 */
public class SqliteStorageService implements StorageService {
    private static final Gson GSON = new Gson();
    private static final Type COUNT_MAP_TYPE = new TypeToken<Map<String, Integer>>() {
    }.getType();
    private static final Type ENTRY_LIST_TYPE = new TypeToken<List<SerializedEntry>>() {
    }.getType();

    private final File dbFile;
    private final File legacyStatsFile;
    private final Logger logger;
    private final ExecutorService dbExecutor;
    private final Object dbLock = new Object();
    private Connection connection;

    private static class SerializedEntry {
        int relX, relY, relZ;
        String material;
        String ownerUuid;

        SerializedEntry(int relX, int relY, int relZ, String material, String ownerUuid) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.material = material;
            this.ownerUuid = ownerUuid;
        }
    }

    public SqliteStorageService(File dataFolder, String relativePath, Logger logger) {
        this.dbFile = new File(dataFolder, relativePath);
        this.legacyStatsFile = new File(dataFolder, "stats.yml");
        this.logger = logger;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AntiRedstoneLag-SQLite");
            t.setDaemon(true);
            return t;
        });
    }

    public static boolean isDriverAvailable() {
        try {
            Class.forName("org.sqlite.JDBC");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String backendName() {
        return "SQLite";
    }

    @Override
    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            synchronized (dbLock) {
                if (this.connection != null) {
                    return;
                }
                this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL;");
                    st.execute("PRAGMA synchronous=NORMAL;");
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS arl_block_owners (
                                world_uuid TEXT NOT NULL,
                                chunk_key INTEGER NOT NULL,
                                block_key INTEGER NOT NULL,
                                owner_uuid TEXT NOT NULL,
                                updated_at INTEGER NOT NULL,
                                PRIMARY KEY (world_uuid, block_key)
                            );""");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_owners_chunk ON arl_block_owners (world_uuid, chunk_key);");
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS arl_snapshots (
                                id TEXT PRIMARY KEY,
                                timestamp INTEGER NOT NULL,
                                world_name TEXT NOT NULL,
                                x INTEGER NOT NULL,
                                y INTEGER NOT NULL,
                                z INTEGER NOT NULL,
                                trigger_material TEXT NOT NULL,
                                chunk_updates INTEGER NOT NULL,
                                block_updates INTEGER NOT NULL,
                                culprit_uuid TEXT,
                                culprit_name TEXT NOT NULL,
                                reason TEXT NOT NULL,
                                counts_json TEXT NOT NULL,
                                entries_json TEXT NOT NULL
                            );""");
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS arl_stats (
                                id INTEGER PRIMARY KEY CHECK (id = 1),
                                total_removals INTEGER NOT NULL,
                                removals_today INTEGER NOT NULL,
                                total_freezes INTEGER NOT NULL,
                                total_lockdowns INTEGER NOT NULL,
                                total_entities_cleared INTEGER NOT NULL,
                                last_reset_time INTEGER NOT NULL
                            );""");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize SQLite storage", e);
        }
    }

    @Override
    public void close() {
        dbExecutor.shutdown();
        try {
            dbExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (dbLock) {
            if (connection != null) {
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                } catch (SQLException ignored) {
                }
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
                connection = null;
            }
        }
    }

    @Override
    public void saveBlockOwnerAsync(UUID worldId, long chunkKey, long blockKey, UUID ownerUuid) {
        if (worldId == null || ownerUuid == null) return;
        long now = System.currentTimeMillis();
        dbExecutor.execute(() -> {
            synchronized (dbLock) {
                if (connection == null) return;
                String sql = """
                        INSERT INTO arl_block_owners (world_uuid, chunk_key, block_key, owner_uuid, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(world_uuid, block_key) DO UPDATE SET
                            chunk_key = excluded.chunk_key,
                            owner_uuid = excluded.owner_uuid,
                            updated_at = excluded.updated_at;
                        """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, worldId.toString());
                    ps.setLong(2, chunkKey);
                    ps.setLong(3, blockKey);
                    ps.setString(4, ownerUuid.toString());
                    ps.setLong(5, now);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    logWarn("Failed to upsert block owner: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void removeBlockOwnerAsync(UUID worldId, long chunkKey, long blockKey) {
        if (worldId == null) return;
        dbExecutor.execute(() -> {
            synchronized (dbLock) {
                if (connection == null) return;
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM arl_block_owners WHERE world_uuid = ? AND block_key = ?;")) {
                    ps.setString(1, worldId.toString());
                    ps.setLong(2, blockKey);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    logWarn("Failed to delete block owner: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void removeChunkOwnersAsync(UUID worldId, long chunkKey) {
        if (worldId == null) return;
        dbExecutor.execute(() -> {
            synchronized (dbLock) {
                if (connection == null) return;
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM arl_block_owners WHERE world_uuid = ? AND chunk_key = ?;")) {
                    ps.setString(1, worldId.toString());
                    ps.setLong(2, chunkKey);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    logWarn("Failed to delete chunk owners: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public Map<Long, UUID> loadChunkOwners(UUID worldId, long chunkKey) {
        if (worldId == null) return Map.of();
        synchronized (dbLock) {
            if (connection == null) return Map.of();
            Map<Long, UUID> result = new HashMap<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT block_key, owner_uuid FROM arl_block_owners WHERE world_uuid = ? AND chunk_key = ?;")) {
                ps.setString(1, worldId.toString());
                ps.setLong(2, chunkKey);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long bk = rs.getLong(1);
                        try {
                            result.put(bk, UUID.fromString(rs.getString(2)));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            } catch (SQLException e) {
                logWarn("Failed to load chunk owners: " + e.getMessage());
            }
            return result;
        }
    }

    @Override
    public CompletableFuture<Map<Long, UUID>> loadChunkOwnersAsync(UUID worldId, long chunkKey) {
        return CompletableFuture.supplyAsync(() -> loadChunkOwners(worldId, chunkKey), dbExecutor);
    }

    @Override
    public void saveSnapshotAsync(SnapshotRecord snapshot) {
        if (snapshot == null) return;
        Map<String, Integer> counts = new LinkedHashMap<>();
        snapshot.componentCounts.forEach((m, c) -> counts.put(m.name(), c));
        List<SerializedEntry> entries = new ArrayList<>(snapshot.blockEntries.size());
        for (SnapshotRecord.BlockEntry be : snapshot.blockEntries) {
            entries.add(new SerializedEntry(be.relX, be.relY, be.relZ, be.material.name(),
                    be.ownerUuid != null ? be.ownerUuid.toString() : null));
        }
        String countsJson = GSON.toJson(counts);
        String entriesJson = GSON.toJson(entries);

        dbExecutor.execute(() -> {
            synchronized (dbLock) {
                if (connection == null) return;
                String sql = """
                        INSERT OR REPLACE INTO arl_snapshots
                        (id, timestamp, world_name, x, y, z, trigger_material, chunk_updates, block_updates,
                         culprit_uuid, culprit_name, reason, counts_json, entries_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                        """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, snapshot.id);
                    ps.setLong(2, snapshot.timestamp);
                    ps.setString(3, snapshot.worldName);
                    ps.setInt(4, snapshot.x);
                    ps.setInt(5, snapshot.y);
                    ps.setInt(6, snapshot.z);
                    ps.setString(7, snapshot.triggerMaterial.name());
                    ps.setInt(8, snapshot.chunkUpdates);
                    ps.setInt(9, snapshot.blockUpdates);
                    ps.setString(10, snapshot.culpritUuid != null ? snapshot.culpritUuid.toString() : null);
                    ps.setString(11, snapshot.culpritName);
                    ps.setString(12, snapshot.reason);
                    ps.setString(13, countsJson);
                    ps.setString(14, entriesJson);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    logWarn("Failed to save snapshot: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void deleteSnapshotAsync(String id) {
        if (id == null) return;
        dbExecutor.execute(() -> {
            synchronized (dbLock) {
                if (connection == null) return;
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM arl_snapshots WHERE id = ?;")) {
                    ps.setString(1, id.toUpperCase());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    logWarn("Failed to delete snapshot: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void clearSnapshotsAsync() {
        dbExecutor.execute(() -> {
            synchronized (dbLock) {
                if (connection == null) return;
                try (Statement st = connection.createStatement()) {
                    st.execute("DELETE FROM arl_snapshots;");
                } catch (SQLException e) {
                    logWarn("Failed to clear snapshots: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public List<SnapshotRecord> loadSnapshots(int limit) {
        List<SnapshotRecord> list = new ArrayList<>();
        synchronized (dbLock) {
            if (connection == null) return list;
            String sql = "SELECT id, timestamp, world_name, x, y, z, trigger_material, chunk_updates, block_updates, " +
                    "culprit_uuid, culprit_name, reason, counts_json, entries_json FROM arl_snapshots ORDER BY timestamp DESC LIMIT ?;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(readSnapshotRow(rs));
                    }
                }
            } catch (SQLException e) {
                logWarn("Failed to load snapshots from SQLite: " + e.getMessage());
            }
        }
        return list;
    }

    private SnapshotRecord readSnapshotRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        long ts = rs.getLong("timestamp");
        String world = rs.getString("world_name");
        int x = rs.getInt("x");
        int y = rs.getInt("y");
        int z = rs.getInt("z");
        Material trigger = Material.matchMaterial(rs.getString("trigger_material"));
        if (trigger == null) trigger = Material.REDSTONE_WIRE;
        int chunkUps = rs.getInt("chunk_updates");
        int blockUps = rs.getInt("block_updates");
        UUID culpritUuid = parseUuid(rs.getString("culprit_uuid"));
        String culpritName = rs.getString("culprit_name");
        String reason = rs.getString("reason");

        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        Map<String, Integer> rawCounts = GSON.fromJson(rs.getString("counts_json"), COUNT_MAP_TYPE);
        if (rawCounts != null) {
            for (var e : rawCounts.entrySet()) {
                Material m = Material.matchMaterial(e.getKey());
                if (m != null) counts.put(m, e.getValue());
            }
        }

        List<SnapshotRecord.BlockEntry> entries = new ArrayList<>();
        List<SerializedEntry> rawEntries = GSON.fromJson(rs.getString("entries_json"), ENTRY_LIST_TYPE);
        if (rawEntries != null) {
            for (SerializedEntry se : rawEntries) {
                Material m = Material.matchMaterial(se.material);
                if (m != null) {
                    entries.add(new SnapshotRecord.BlockEntry(se.relX, se.relY, se.relZ, m, parseUuid(se.ownerUuid)));
                }
            }
        }

        return new SnapshotRecord(id, ts, world, x, y, z, trigger, chunkUps, blockUps,
                culpritUuid, culpritName, reason, counts, entries);
    }

    @Override
    public PluginStats loadStats() {
        PluginStats stats = new PluginStats();
        boolean found = false;
        synchronized (dbLock) {
            if (connection != null) {
                try (Statement st = connection.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM arl_stats WHERE id = 1;")) {
                    if (rs.next()) {
                        found = true;
                        stats.setTotalRemovals(rs.getInt("total_removals"));
                        stats.setRemovalsToday(rs.getInt("removals_today"));
                        stats.setTotalFreezes(rs.getInt("total_freezes"));
                        stats.setTotalLockdowns(rs.getInt("total_lockdowns"));
                        stats.setTotalEntitiesCleared(rs.getLong("total_entities_cleared"));
                        stats.setLastResetTime(rs.getLong("last_reset_time"));
                    }
                } catch (SQLException e) {
                    logWarn("Failed to load stats from SQLite: " + e.getMessage());
                }
            }
        }

        if (!found && legacyStatsFile.exists()) {
            try {
                YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyStatsFile);
                stats.setTotalRemovals(legacy.getInt("total-removals", 0));
                stats.setRemovalsToday(legacy.getInt("removals-today", 0));
                stats.setLastResetTime(legacy.getLong("last-reset-time", System.currentTimeMillis()));
                if (logger != null) {
                    logger.info("Migrated v1 stats.yml into v2 SQLite database.");
                }
            } catch (Exception e) {
                logWarn("Failed to migrate v1 stats.yml: " + e.getMessage());
            }
            saveStats(stats);
        }

        stats.rolloverIfNeeded(System.currentTimeMillis());
        return stats;
    }

    @Override
    public void saveStats(PluginStats stats) {
        if (stats == null) return;
        writeStatsSync(stats.snapshot());
    }

    @Override
    public void saveStatsAsync(PluginStats stats) {
        if (stats == null) return;
        PluginStats.StatsSnapshot snap = stats.snapshot();
        dbExecutor.execute(() -> writeStatsSync(snap));
    }

    private void writeStatsSync(PluginStats.StatsSnapshot snap) {
        synchronized (dbLock) {
            if (connection == null) return;
            String sql = """
                    INSERT OR REPLACE INTO arl_stats
                    (id, total_removals, removals_today, total_freezes, total_lockdowns, total_entities_cleared, last_reset_time)
                    VALUES (1, ?, ?, ?, ?, ?, ?);
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, snap.totalRemovals());
                ps.setInt(2, snap.removalsToday());
                ps.setInt(3, snap.totalFreezes());
                ps.setInt(4, snap.totalLockdowns());
                ps.setLong(5, snap.totalEntitiesCleared());
                ps.setLong(6, snap.lastResetTime());
                ps.executeUpdate();
            } catch (SQLException e) {
                logWarn("Failed to persist stats to SQLite: " + e.getMessage());
            }
        }
    }

    @Override
    public void pruneExpiredOwnersAsync(int retentionDays) {
        long cutoff = System.currentTimeMillis() - (Math.max(1L, retentionDays) * PluginStats.DAY_MS);
        dbExecutor.execute(() -> {
            synchronized (dbLock) {
                if (connection == null) return;
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM arl_block_owners WHERE updated_at < ?;")) {
                    ps.setLong(1, cutoff);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    logWarn("Failed to prune old block owners: " + e.getMessage());
                }
            }
        });
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void logWarn(String msg) {
        if (logger != null) {
            logger.warning(msg);
        }
    }
}
