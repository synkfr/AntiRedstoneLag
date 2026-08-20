package org.ayosynk.antiRedstoneLag.manager;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.configuration.file.YamlConfiguration;

public class CounterManager {

    // -----------------------------------------------------------------------
    // Event result constants returned by processEvent()
    // -----------------------------------------------------------------------
    /** No action needed. */
    public static final int EVENT_NONE    = 0;
    /** Block owner should receive a threshold-proximity warning. */
    public static final int EVENT_WARN    = 1;
    /** Redstone activity exceeded both thresholds; block should be silenced/removed. */
    public static final int EVENT_DISABLE = 2;

    // -----------------------------------------------------------------------
    // Hotspot data record
    // -----------------------------------------------------------------------

    /**
     * Represents a cluster of spatially adjacent chunks with elevated redstone activity.
     * The center coordinates are activity-weighted (centre of mass, not geometric centre).
     */
    public static class HotspotGroup {
        public final String worldName;
        public final UUID   worldId;
        /** Block X of the activity-weighted centre of this hotspot cluster. */
        public final int centerBlockX;
        /** Block Z of the activity-weighted centre of this hotspot cluster. */
        public final int centerBlockZ;
        /** Sum of all chunk-level redstone update counts in this cluster. */
        public final int totalActivity;
        /** Number of chunks that make up this cluster. */
        public final int chunkCount;

        HotspotGroup(String worldName, UUID worldId,
                     int centerBlockX, int centerBlockZ,
                     int totalActivity, int chunkCount) {
            this.worldName     = worldName;
            this.worldId       = worldId;
            this.centerBlockX  = centerBlockX;
            this.centerBlockZ  = centerBlockZ;
            this.totalActivity = totalActivity;
            this.chunkCount    = chunkCount;
        }
    }

    private final AntiRedstoneLag plugin;
    private static final long ALERT_COOLDOWN_MS   = 1000;
    private static final long WARNING_COOLDOWN_MS = 5000;
    private static final long DAY_MS              = 24L * 60 * 60 * 1000;

    // Lockdown: World UUID -> (ChunkKey -> expiration epoch-ms)
    // Accessed under lockdownLock to keep Long2LongOpenHashMap mutation safe.
    private final Map<UUID, Long2LongOpenHashMap> chunkLockdowns = new ConcurrentHashMap<>();
    /** Guards all lockdown map reads/writes (separate from counterLock to avoid contention). */
    private final Object lockdownLock = new Object();

    // Per-world primitive counter maps – guarded by counterLock
    private final Map<UUID, Long2IntOpenHashMap> chunkCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> blockCounters  = new ConcurrentHashMap<>();

    /** Per-player last-warned timestamp; boxed Long is acceptable here (warning is rare). */
    private final Map<UUID, Long> warnedPlayers = new ConcurrentHashMap<>();
    /** Guards chunkCounters and blockCounters. */
    private final Object counterLock = new Object();

    private final ConfigManager  configManager;
    private final MessageManager messageManager;
    private final LogManager     logManager;
    private final File           statsFile;

    private final AtomicInteger totalRemovals = new AtomicInteger(0);
    private final AtomicInteger removalsToday = new AtomicInteger(0);
    private final AtomicLong    lastAlertTime   = new AtomicLong(0);
    private final AtomicLong    lastWarningTime = new AtomicLong(0);
    private volatile long lastResetTime = System.currentTimeMillis();

    public CounterManager(AntiRedstoneLag plugin, ConfigManager configManager,
                          MessageManager messageManager, LogManager logManager, File dataFolder) {
        this.plugin         = plugin;
        this.configManager  = configManager;
        this.messageManager = messageManager;
        this.logManager     = logManager;
        this.statsFile      = new File(dataFolder, "stats.yml");
        loadStats();
    }

    // -----------------------------------------------------------------------
    // Combined hot-path entry point
    // -----------------------------------------------------------------------

    /**
     * Increments the chunk and block counters for this redstone event and
     * returns the action the listener should take.
     *
     * <p>This merges what were previously three separate synchronized blocks
     * ({@code incrementCounters}, {@code shouldWarn}, {@code shouldDisable})
     * into a single lock acquisition, cutting monitor-enter overhead by ~67 %
     * on the hot {@code BlockRedstoneEvent} path.</p>
     *
     * @return {@link #EVENT_DISABLE}, {@link #EVENT_WARN}, or {@link #EVENT_NONE}
     */
    public int processEvent(UUID worldId, long chunkKey, long blockKey) {
        synchronized (counterLock) {
            Long2IntOpenHashMap cMap = chunkCounters.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap());
            Long2IntOpenHashMap bMap = blockCounters.computeIfAbsent(worldId,  k -> new Long2IntOpenHashMap());

            // addTo() returns the OLD value; +1 gives the new post-increment value.
            int chunkVal = cMap.addTo(chunkKey, 1) + 1;
            int blockVal = bMap.addTo(blockKey,  1) + 1;

            int chunkThreshold = configManager.getChunkThreshold();
            int blockThreshold = configManager.getBlockThreshold();

            if (chunkVal > chunkThreshold && blockVal > blockThreshold) {
                return EVENT_DISABLE;
            }

            if (configManager.isWarningEnabled()) {
                int chunkWarn = configManager.getChunkWarningThreshold();
                int blockWarn = configManager.getBlockWarningThreshold();
                if ((chunkVal >= chunkWarn && chunkVal <= chunkThreshold) ||
                    (blockVal >= blockWarn && blockVal <= blockThreshold)) {
                    return EVENT_WARN;
                }
            }

            return EVENT_NONE;
        }
    }

    // -----------------------------------------------------------------------
    // Hotspot analysis
    // -----------------------------------------------------------------------

    /**
     * Snapshots the current chunk counters, groups spatially adjacent chunks
     * into clusters using BFS (4-way connectivity within each world), computes
     * the activity-weighted centre coordinate of each cluster, then returns the
     * top {@code maxGroups} clusters sorted by total activity descending.
     *
     * <p>This is an on-demand, off-hot-path operation called only by the
     * {@code /arl hotspots} command. The counters are only held under lock long
     * enough to take a shallow copy; all BFS work is done outside the lock.</p>
     *
     * @param maxGroups maximum number of groups to return (e.g. 50)
     * @return immutable sorted list of hotspot groups
     */
    public List<HotspotGroup> getHotspots(int maxGroups) {
        // --- Snapshot under lock (fast copy, no BFS inside lock) ---
        Map<UUID, Long2IntOpenHashMap> snapshot = new HashMap<>();
        synchronized (counterLock) {
            for (Map.Entry<UUID, Long2IntOpenHashMap> e : chunkCounters.entrySet()) {
                Long2IntOpenHashMap src = e.getValue();
                if (!src.isEmpty()) {
                    snapshot.put(e.getKey(), new Long2IntOpenHashMap(src));
                }
            }
        }

        List<HotspotGroup> groups = new ArrayList<>();

        for (Map.Entry<UUID, Long2IntOpenHashMap> worldEntry : snapshot.entrySet()) {
            UUID worldId                = worldEntry.getKey();
            Long2IntOpenHashMap chunkMap = worldEntry.getValue();

            org.bukkit.World world = Bukkit.getWorld(worldId);
            String worldName = world != null ? world.getName() : worldId.toString();

            // BFS: find all connected components (4-way adjacency)
            LongOpenHashSet visited = new LongOpenHashSet(chunkMap.size() * 2);

            for (long startKey : new LongOpenHashSet(chunkMap.keySet())) {
                if (!visited.add(startKey)) continue; // already part of a group

                // BFS queue; using boxed Long here is fine – this is a command path
                ArrayDeque<Long> queue   = new ArrayDeque<>();
                List<Long>       cluster = new ArrayList<>();
                queue.add(startKey);
                cluster.add(startKey);

                while (!queue.isEmpty()) {
                    long key = queue.poll();
                    int cx = (int) (key >> 32);
                    int cz = (int) key;            // sign-extending int cast handles negatives

                    // Visit 4 orthogonal neighbours
                    long[] neighbors = {
                        packChunk(cx + 1, cz), packChunk(cx - 1, cz),
                        packChunk(cx, cz + 1), packChunk(cx, cz - 1)
                    };
                    for (long nb : neighbors) {
                        if (chunkMap.containsKey(nb) && visited.add(nb)) {
                            queue.add(nb);
                            cluster.add(nb);
                        }
                    }
                }

                // Compute activity-weighted centre (centre of mass, not geometric centre)
                long weightedSumX = 0, weightedSumZ = 0, totalActivity = 0;
                for (long key : cluster) {
                    int cx       = (int) (key >> 32);
                    int cz       = (int) key;
                    int activity = chunkMap.get(key);
                    weightedSumX  += (long) cx * activity;
                    weightedSumZ  += (long) cz * activity;
                    totalActivity += activity;
                }

                int centreChunkX  = (int) (weightedSumX / totalActivity);
                int centreChunkZ  = (int) (weightedSumZ / totalActivity);
                // Convert chunk coords to block coords (centre of the chunk)
                int centreBlockX  = centreChunkX * 16 + 8;
                int centreBlockZ  = centreChunkZ * 16 + 8;

                groups.add(new HotspotGroup(
                        worldName, worldId,
                        centreBlockX, centreBlockZ,
                        (int) Math.min(totalActivity, Integer.MAX_VALUE),
                        cluster.size()));
            }
        }

        // Sort by total activity descending
        groups.sort((a, b) -> Integer.compare(b.totalActivity, a.totalActivity));

        return groups.size() > maxGroups ? new ArrayList<>(groups.subList(0, maxGroups)) : groups;
    }

    // -----------------------------------------------------------------------
    // Lockdown (guarded by lockdownLock)
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if the specified chunk is currently locked down.
     * Expired entries are lazily removed on access.
     */
    public boolean isChunkLocked(UUID worldId, long chunkKey) {
        synchronized (lockdownLock) {
            Long2LongOpenHashMap lockdowns = chunkLockdowns.get(worldId);
            if (lockdowns == null) return false;
            long expiration = lockdowns.get(chunkKey);
            if (expiration == 0L) return false;
            if (System.currentTimeMillis() > expiration) {
                lockdowns.remove(chunkKey);
                return false;
            }
            return true;
        }
    }

    /**
     * Locks a chunk for {@code durationSeconds}.
     *
     * @return {@code true} if the lockdown was newly applied;
     *         {@code false} if it was already locked.
     */
    public boolean lockdownChunk(UUID worldId, long chunkKey, long durationSeconds) {
        synchronized (lockdownLock) {
            Long2LongOpenHashMap lockdowns = chunkLockdowns.computeIfAbsent(worldId, k -> new Long2LongOpenHashMap());
            long now = System.currentTimeMillis();
            if (lockdowns.containsKey(chunkKey) && lockdowns.get(chunkKey) > now) {
                return false;
            }
            lockdowns.put(chunkKey, now + (durationSeconds * 1000L));
            return true;
        }
    }

    /** Returns remaining lockdown time in milliseconds, or 0 if not locked. */
    public long getLockdownRemaining(UUID worldId, long chunkKey) {
        synchronized (lockdownLock) {
            Long2LongOpenHashMap lockdowns = chunkLockdowns.get(worldId);
            if (lockdowns == null) return 0;
            long expiration = lockdowns.get(chunkKey);
            if (expiration == 0L) return 0;
            long remaining = expiration - System.currentTimeMillis();
            return remaining > 0 ? remaining : 0;
        }
    }

    // -----------------------------------------------------------------------
    // Warnings
    // -----------------------------------------------------------------------

    public void sendWarning(Location location, Material material, UUID ownerUuid) {
        if (location == null || location.getWorld() == null || !canSendWarning() || ownerUuid == null) return;

        long chunkKey = packChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        int currentCount;
        synchronized (counterLock) {
            Long2IntOpenHashMap cMap = chunkCounters.get(location.getWorld().getUID());
            currentCount = cMap != null ? cMap.get(chunkKey) : 0;
        }

        int threshold = configManager.getChunkThreshold();
        int percent   = threshold > 0 ? (currentCount * 100) / threshold : 0;

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner != null && owner.isOnline()) {
            Long lastWarned = warnedPlayers.get(ownerUuid);
            long now = System.currentTimeMillis();
            if (lastWarned == null || now - lastWarned >= WARNING_COOLDOWN_MS) {
                owner.sendMessage(messageManager.getMessage("alerts.redstone-warning",
                                "&#FFD93D⚠ &eWarning: &7Redstone activity at &e{x}, {y}, {z} &7is at &c{percent}% &7of threshold!")
                        .replaceText(t -> t.matchLiteral("{x}").replacement(String.valueOf(location.getBlockX())))
                        .replaceText(t -> t.matchLiteral("{y}").replacement(String.valueOf(location.getBlockY())))
                        .replaceText(t -> t.matchLiteral("{z}").replacement(String.valueOf(location.getBlockZ())))
                        .replaceText(t -> t.matchLiteral("{world}").replacement(location.getWorld().getName()))
                        .replaceText(t -> t.matchLiteral("{material}").replacement(material.toString()))
                        .replaceText(t -> t.matchLiteral("{percent}").replacement(String.valueOf(percent)))
                        .replaceText(t -> t.matchLiteral("{current}").replacement(String.valueOf(currentCount)))
                        .replaceText(t -> t.matchLiteral("{threshold}").replacement(String.valueOf(threshold))));
                warnedPlayers.put(ownerUuid, now);
            }
        }
    }

    private boolean canSendWarning() {
        long now = System.currentTimeMillis();
        long lastWarning = lastWarningTime.get();
        if (now - lastWarning >= WARNING_COOLDOWN_MS) {
            return lastWarningTime.compareAndSet(lastWarning, now);
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Counter reset & stats
    // -----------------------------------------------------------------------

    public void resetCounters() {
        if (configManager.isLogPerformance()) logPerformanceStats();
        synchronized (counterLock) {
            chunkCounters.values().forEach(Long2IntOpenHashMap::clear);
            blockCounters.values().forEach(Long2IntOpenHashMap::clear);
        }
        warnedPlayers.clear();
        if (System.currentTimeMillis() - lastResetTime > DAY_MS) {
            removalsToday.set(0);
            lastResetTime = System.currentTimeMillis();
            saveStats();
        }
    }

    public void handleRedstoneRemoval(Location location, Material material, long chunkKey, long blockKey) {
        if (location == null || location.getWorld() == null) return;
        int chunkCount = 0, blockCount = 0;
        synchronized (counterLock) {
            Long2IntOpenHashMap cMap = chunkCounters.get(location.getWorld().getUID());
            Long2IntOpenHashMap bMap = blockCounters.get(location.getWorld().getUID());
            if (cMap != null) chunkCount = cMap.get(chunkKey);
            if (bMap != null) blockCount = bMap.get(blockKey);
        }
        totalRemovals.incrementAndGet();
        removalsToday.incrementAndGet();
        logManager.logRedstoneRemoval(location, material, chunkCount, blockCount, "Exceeded thresholds");
        if (configManager.isAlertsEnabled() && canSendAlert()) {
            sendAlert(location, material, chunkCount, blockCount);
        }
    }

    private boolean canSendAlert() {
        long now = System.currentTimeMillis();
        long lastAlert = lastAlertTime.get();
        if (now - lastAlert >= ALERT_COOLDOWN_MS) {
            return lastAlertTime.compareAndSet(lastAlert, now);
        }
        return false;
    }

    private void sendAlert(Location location, Material material, int chunkCount, int blockCount) {
        if (location.getWorld() == null) return;
        net.kyori.adventure.text.Component alertComponent = messageManager.getMessage("alerts.redstone-removed")
                .replaceText(t -> t.matchLiteral("{x}").replacement(String.valueOf(location.getBlockX())))
                .replaceText(t -> t.matchLiteral("{y}").replacement(String.valueOf(location.getBlockY())))
                .replaceText(t -> t.matchLiteral("{z}").replacement(String.valueOf(location.getBlockZ())))
                .replaceText(t -> t.matchLiteral("{world}").replacement(location.getWorld().getName()))
                .replaceText(t -> t.matchLiteral("{material}").replacement(material.toString()))
                .replaceText(t -> t.matchLiteral("{chunk_count}").replacement(String.valueOf(chunkCount)))
                .replaceText(t -> t.matchLiteral("{block_count}").replacement(String.valueOf(blockCount)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("antiredstonelag.alerts")) {
                player.sendMessage(alertComponent);
            }
        }
        if (configManager.isLogToConsole()) {
            plugin.getServer().getConsoleSender().sendMessage(alertComponent);
        }
    }

    /**
     * Logs performance stats using a single pass over {@code chunkCounters}
     * to collect both the count and the sum (previously two separate loops).
     */
    private void logPerformanceStats() {
        int chunksMonitored = 0, blocksMonitored = 0;
        long sum = 0;
        synchronized (counterLock) {
            for (Long2IntOpenHashMap m : chunkCounters.values()) {
                chunksMonitored += m.size();
                for (int v : m.values()) sum += v;
            }
            for (Long2IntOpenHashMap m : blockCounters.values()) {
                blocksMonitored += m.size();
            }
        }
        double avgUpdates = chunksMonitored > 0 ? (double) sum / chunksMonitored : 0;
        logManager.logPerformanceStats(chunksMonitored, blocksMonitored, avgUpdates);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public AntiRedstoneLag getPlugin() { return plugin; }
    public int getTotalRemovals()      { return totalRemovals.get(); }
    public int getRemovalsToday()      { return removalsToday.get(); }

    /** Returns the number of chunk-keys currently being tracked. */
    public int getChunksMonitored() {
        synchronized (counterLock) {
            int total = 0;
            for (Long2IntOpenHashMap m : chunkCounters.values()) total += m.size();
            return total;
        }
    }

    /** Returns the number of block-keys currently being tracked. */
    public int getBlocksMonitored() {
        synchronized (counterLock) {
            int total = 0;
            for (Long2IntOpenHashMap m : blockCounters.values()) total += m.size();
            return total;
        }
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    private void loadStats() {
        if (!statsFile.exists()) return;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(statsFile);
            totalRemovals.set(config.getInt("total-removals", 0));
            removalsToday.set(config.getInt("removals-today", 0));
            lastResetTime = config.getLong("last-reset-time", System.currentTimeMillis());
            if (System.currentTimeMillis() - lastResetTime > DAY_MS) {
                removalsToday.set(0);
                lastResetTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            logManager.logToFile("ERROR", "Failed to load stats: " + e.getMessage(), null);
        }
    }

    public void saveStats() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("total-removals",  totalRemovals.get());
            config.set("removals-today",  removalsToday.get());
            config.set("last-reset-time", lastResetTime);
            config.save(statsFile);
        } catch (IOException e) {
            logManager.logToFile("ERROR", "Failed to save stats: " + e.getMessage(), null);
        }
    }

    // -----------------------------------------------------------------------
    // Coordinate packing utilities
    // -----------------------------------------------------------------------

    public static long packChunk(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static long packBlock(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}