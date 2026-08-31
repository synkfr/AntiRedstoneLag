package org.ayosynk.antiRedstoneLag.manager;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;

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
    public static final int EVENT_NONE = 0;
    public static final int EVENT_WARN = 1;
    public static final int EVENT_DISABLE = 2;
    public static final int EVENT_FREEZE = 3;

    public static class HotspotGroup {
        public final String worldName;
        public final UUID worldId;
        public final int centerBlockX;
        public final int centerBlockZ;
        public final int totalActivity;
        public final int chunkCount;

        public HotspotGroup(String worldName, UUID worldId, int centerBlockX, int centerBlockZ, int totalActivity, int chunkCount) {
            this.worldName = worldName;
            this.worldId = worldId;
            this.centerBlockX = centerBlockX;
            this.centerBlockZ = centerBlockZ;
            this.totalActivity = totalActivity;
            this.chunkCount = chunkCount;
        }
    }

    private static final long ALERT_COOLDOWN_MS = 1000;
    private static final long WARNING_COOLDOWN_MS = 5000;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;
    private final File statsFile;

    private final Map<UUID, Long2LongOpenHashMap> chunkLockdowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long2LongOpenHashMap> frozenBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> freezeViolations = new ConcurrentHashMap<>();
    private final Map<UUID, Long2LongOpenHashMap> lastPulseTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long2LongOpenHashMap> lastPulseDeltas = new ConcurrentHashMap<>();

    private final Object lockdownLock = new Object();
    private final Object freezeLock = new Object();

    private final Map<UUID, Long2IntOpenHashMap> chunkCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> blockCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warnedPlayers = new ConcurrentHashMap<>();
    private final Object counterLock = new Object();

    private final AtomicInteger totalRemovals = new AtomicInteger(0);
    private final AtomicInteger removalsToday = new AtomicInteger(0);
    private final AtomicLong lastAlertTime = new AtomicLong(0);
    private final AtomicLong lastWarningTime = new AtomicLong(0);
    private volatile long lastResetTime = System.currentTimeMillis();

    public CounterManager(AntiRedstoneLag plugin, ConfigManager configManager, MessageManager messageManager, LogManager logManager, File dataFolder) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
        this.statsFile = new File(dataFolder, "stats.yml");
        loadStats();
    }

    public double getAdaptiveMultiplier() {
        if (!configManager.getPluginConfig().getAdaptive().isEnabled()) return 1.0;
        try {
            double mspt = Bukkit.getAverageTickTime();
            double targetMspt = configManager.getPluginConfig().getAdaptive().getTargetMspt();
            if (mspt <= 35.0) {
                return configManager.getPluginConfig().getAdaptive().getHealthyHeadroomMultiplier();
            } else if (mspt >= 50.0) {
                return 0.5;
            } else if (mspt > targetMspt) {
                return 0.75;
            }
        } catch (Throwable ignored) {
        }
        return 1.0;
    }

    public int processEvent(UUID worldId, long chunkKey, long blockKey, boolean hasNearbyPlayers) {
        long now = System.currentTimeMillis();

        synchronized (freezeLock) {
            Long2LongOpenHashMap worldFrozen = frozenBlocks.get(worldId);
            if (worldFrozen != null) {
                long expiration = worldFrozen.get(blockKey);
                if (expiration > 0) {
                    if (now < expiration) {
                        return EVENT_FREEZE;
                    } else {
                        worldFrozen.remove(blockKey);
                    }
                }
            }
        }

        synchronized (counterLock) {
            Long2IntOpenHashMap cMap = chunkCounters.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap());
            Long2IntOpenHashMap bMap = blockCounters.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap());

            int chunkVal = cMap.addTo(chunkKey, 1) + 1;
            int blockVal = bMap.addTo(blockKey, 1) + 1;

            double multiplier = getAdaptiveMultiplier();
            if (!hasNearbyPlayers && configManager.getPluginConfig().getProximity().isEnabled()) {
                multiplier *= configManager.getPluginConfig().getProximity().getUnattendedStrictMultiplier();
            }

            if (configManager.getPluginConfig().getFingerprint().isEnabled()) {
                Long2LongOpenHashMap pTimes = lastPulseTimes.computeIfAbsent(worldId, k -> new Long2LongOpenHashMap());
                Long2LongOpenHashMap pDeltas = lastPulseDeltas.computeIfAbsent(worldId, k -> new Long2LongOpenHashMap());
                long lastTime = pTimes.get(blockKey);
                if (lastTime > 0) {
                    long delta = now - lastTime;
                    long prevDelta = pDeltas.get(blockKey);
                    if (prevDelta > 0 && Math.abs(delta - prevDelta) <= 50 && delta <= 300) {
                        multiplier *= configManager.getPluginConfig().getFingerprint().getClockStrictness();
                    }
                    pDeltas.put(blockKey, delta);
                }
                pTimes.put(blockKey, now);
            }

            int chunkThreshold = (int) Math.max(1, configManager.getChunkThreshold() * multiplier);
            int blockThreshold = (int) Math.max(1, configManager.getBlockThreshold() * multiplier);

            if (chunkVal > chunkThreshold && blockVal > blockThreshold) {
                PluginConfig.RemovalAction action = configManager.getPluginConfig().getRemovalAction();
                if (action == PluginConfig.RemovalAction.FREEZE) {
                    int violations;
                    synchronized (freezeLock) {
                        Long2IntOpenHashMap vMap = freezeViolations.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap());
                        violations = vMap.addTo(blockKey, 1) + 1;
                    }
                    if (violations <= configManager.getPluginConfig().getFreeze().getMaxFreezeAttempts()) {
                        int duration = configManager.getPluginConfig().getFreeze().getDurationSeconds();
                        freezeBlock(worldId, blockKey, duration);
                        return EVENT_FREEZE;
                    }
                }
                return EVENT_DISABLE;
            }

            if (configManager.isWarningEnabled()) {
                int chunkWarn = (chunkThreshold * configManager.getWarningThresholdPercent()) / 100;
                int blockWarn = (blockThreshold * configManager.getWarningThresholdPercent()) / 100;
                if ((chunkVal >= chunkWarn && chunkVal <= chunkThreshold) ||
                    (blockVal >= blockWarn && blockVal <= blockThreshold)) {
                    return EVENT_WARN;
                }
            }

            return EVENT_NONE;
        }
    }

    public boolean isBlockFrozen(UUID worldId, long blockKey) {
        synchronized (freezeLock) {
            Long2LongOpenHashMap worldFrozen = frozenBlocks.get(worldId);
            if (worldFrozen == null) return false;
            long expiration = worldFrozen.get(blockKey);
            if (expiration == 0L) return false;
            if (System.currentTimeMillis() > expiration) {
                worldFrozen.remove(blockKey);
                return false;
            }
            return true;
        }
    }

    public void freezeBlock(UUID worldId, long blockKey, int durationSeconds) {
        synchronized (freezeLock) {
            Long2LongOpenHashMap worldFrozen = frozenBlocks.computeIfAbsent(worldId, k -> new Long2LongOpenHashMap());
            worldFrozen.put(blockKey, System.currentTimeMillis() + (durationSeconds * 1000L));
        }
    }

    public long getFreezeRemaining(UUID worldId, long blockKey) {
        synchronized (freezeLock) {
            Long2LongOpenHashMap worldFrozen = frozenBlocks.get(worldId);
            if (worldFrozen == null) return 0;
            long expiration = worldFrozen.get(blockKey);
            if (expiration == 0L) return 0;
            long remaining = expiration - System.currentTimeMillis();
            return remaining > 0 ? remaining : 0;
        }
    }

    public List<HotspotGroup> getHotspots(int maxGroups) {
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
            UUID worldId = worldEntry.getKey();
            Long2IntOpenHashMap chunkMap = worldEntry.getValue();

            org.bukkit.World world = Bukkit.getWorld(worldId);
            String worldName = world != null ? world.getName() : worldId.toString();

            LongOpenHashSet visited = new LongOpenHashSet(chunkMap.size() * 2);

            for (long startKey : new LongOpenHashSet(chunkMap.keySet())) {
                if (!visited.add(startKey)) continue;

                ArrayDeque<Long> queue = new ArrayDeque<>();
                List<Long> cluster = new ArrayList<>();
                queue.add(startKey);
                cluster.add(startKey);

                while (!queue.isEmpty()) {
                    long key = queue.poll();
                    int cx = (int) (key >> 32);
                    int cz = (int) key;

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

                long weightedSumX = 0, weightedSumZ = 0, totalActivity = 0;
                for (long key : cluster) {
                    int cx = (int) (key >> 32);
                    int cz = (int) key;
                    int activity = chunkMap.get(key);
                    weightedSumX += (long) cx * activity;
                    weightedSumZ += (long) cz * activity;
                    totalActivity += activity;
                }

                int centreChunkX = (int) (weightedSumX / totalActivity);
                int centreChunkZ = (int) (weightedSumZ / totalActivity);
                int centreBlockX = centreChunkX * 16 + 8;
                int centreBlockZ = centreChunkZ * 16 + 8;

                groups.add(new HotspotGroup(
                        worldName, worldId,
                        centreBlockX, centreBlockZ,
                        (int) Math.min(totalActivity, Integer.MAX_VALUE),
                        cluster.size()));
            }
        }

        groups.sort((a, b) -> Integer.compare(b.totalActivity, a.totalActivity));
        return groups.size() > maxGroups ? new ArrayList<>(groups.subList(0, maxGroups)) : groups;
    }

    public int getChunkUpdates(UUID worldId, long chunkKey) {
        synchronized (counterLock) {
            Long2IntOpenHashMap cMap = chunkCounters.get(worldId);
            return cMap != null ? cMap.get(chunkKey) : 0;
        }
    }

    public int getBlockUpdates(UUID worldId, long blockKey) {
        synchronized (counterLock) {
            Long2IntOpenHashMap bMap = blockCounters.get(worldId);
            return bMap != null ? bMap.get(blockKey) : 0;
        }
    }

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

    public void sendWarning(Location location, Material material, UUID ownerUuid) {
        if (location == null || location.getWorld() == null || !canSendWarning() || ownerUuid == null) return;

        long chunkKey = packChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        int currentCount;
        synchronized (counterLock) {
            Long2IntOpenHashMap cMap = chunkCounters.get(location.getWorld().getUID());
            currentCount = cMap != null ? cMap.get(chunkKey) : 0;
        }

        int threshold = configManager.getChunkThreshold();
        int percent = threshold > 0 ? (currentCount * 100) / threshold : 0;

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner != null && owner.isOnline()) {
            Long lastWarned = warnedPlayers.get(ownerUuid);
            long now = System.currentTimeMillis();
            if (lastWarned == null || now - lastWarned >= WARNING_COOLDOWN_MS) {
                owner.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getAlerts().getRedstoneWarning())
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

    public void resetCounters() {
        if (configManager.isLogPerformance()) {
            logPerformanceStats();
        }
        synchronized (counterLock) {
            chunkCounters.values().forEach(Long2IntOpenHashMap::clear);
            blockCounters.values().forEach(Long2IntOpenHashMap::clear);
        }
        warnedPlayers.clear();
        if (System.currentTimeMillis() - lastResetTime > DAY_MS) {
            removalsToday.set(0);
            synchronized (freezeLock) {
                freezeViolations.values().forEach(Long2IntOpenHashMap::clear);
            }
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
        
        net.kyori.adventure.text.Component alertComponent = messageManager.parseMessage(messageManager.getMessagesConfig().getAlerts().getRedstoneRemoved())
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
            Bukkit.getConsoleSender().sendMessage(alertComponent);
        }
    }

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

    public AntiRedstoneLag getPlugin() {
        return plugin;
    }

    public int getTotalRemovals() {
        return totalRemovals.get();
    }

    public int getRemovalsToday() {
        return removalsToday.get();
    }

    public int getChunksMonitored() {
        synchronized (counterLock) {
            int total = 0;
            for (Long2IntOpenHashMap m : chunkCounters.values()) total += m.size();
            return total;
        }
    }

    public int getBlocksMonitored() {
        synchronized (counterLock) {
            int total = 0;
            for (Long2IntOpenHashMap m : blockCounters.values()) total += m.size();
            return total;
        }
    }

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
            config.set("total-removals", totalRemovals.get());
            config.set("removals-today", removalsToday.get());
            config.set("last-reset-time", lastResetTime);
            config.save(statsFile);
        } catch (IOException e) {
            logManager.logToFile("ERROR", "Failed to save stats: " + e.getMessage(), null);
        }
    }

    public static long packChunk(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static long packBlock(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}