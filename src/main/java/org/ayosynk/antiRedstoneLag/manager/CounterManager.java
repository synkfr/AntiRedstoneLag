package org.ayosynk.antiRedstoneLag.manager;

import net.kyori.adventure.text.Component;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CounterManager {
    public static final int EVENT_NONE = 0;
    public static final int EVENT_WARN = 1;
    public static final int EVENT_DISABLE = 2;
    public static final int EVENT_FREEZE = 3;
    public static final int EVENT_NOTIFY = 4;

    private static final boolean FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

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

    private static final long ALERT_COOLDOWN_MS = 5000L;
    private static final long NOTIFY_COOLDOWN_MS = 15000L;
    private static final long WARNING_COOLDOWN_MS = 15000L;
    private static final long PULSE_RETENTION_MS = 60000L;
    private static final long PRUNE_INTERVAL_MS = 60000L;
    private static final long DAY_MS = 24L * 60 * 60 * 1000L;

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;
    private final File statsFile;

    private final Map<UUID, Long2LongOpenHashMap> chunkLockdowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long2LongOpenHashMap> frozenBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> freezeViolations = new ConcurrentHashMap<>();
    private final Map<UUID, Long2LongOpenHashMap> freezeViolationExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> sustainedViolations = new ConcurrentHashMap<>();
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
    private final AtomicLong lastNotifyTime = new AtomicLong(0);
    private final AtomicLong lastLagAlertTime = new AtomicLong(0);
    private volatile long lastResetTime = System.currentTimeMillis();
    private volatile long lastPruneTime = System.currentTimeMillis();

    private volatile double cachedAdaptiveMultiplier = 1.0;
    private volatile boolean cachedServerLagging = false;
    private volatile double cachedTps = 20.0;
    private volatile double cachedMspt = 0.0;

    public CounterManager(AntiRedstoneLag plugin, ConfigManager configManager, MessageManager messageManager, LogManager logManager, File dataFolder) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
        this.statsFile = new File(dataFolder, "stats.yml");
        loadStats();
        updatePerformanceSnapshot();
    }

    public void updatePerformanceSnapshot() {
        if (FOLIA || configManager == null) {
            cachedAdaptiveMultiplier = 1.0;
            cachedServerLagging = false;
            return;
        }
        try {
            double mspt = Bukkit.getAverageTickTime();
            double tps = Bukkit.getTPS()[0];
            cachedMspt = mspt;
            cachedTps = tps;

            PluginConfig.AdaptiveConfig adaptive = configManager.getPluginConfig().getAdaptive();
            double minTps = adaptive.getMinTps();
            double targetMspt = adaptive.getTargetMspt();
            cachedServerLagging = (tps < minTps) || (mspt > targetMspt);

            if (!adaptive.isEnabled()) {
                cachedAdaptiveMultiplier = 1.0;
                return;
            }
            if (tps < minTps || mspt >= 50.0) {
                cachedAdaptiveMultiplier = 0.5;
            } else if (mspt > targetMspt) {
                cachedAdaptiveMultiplier = 0.75;
            } else if (mspt <= 35.0 && tps >= 19.5) {
                cachedAdaptiveMultiplier = adaptive.getHealthyHeadroomMultiplier();
            } else {
                cachedAdaptiveMultiplier = 1.0;
            }
        } catch (Throwable ignored) {
            cachedAdaptiveMultiplier = 1.0;
            cachedServerLagging = false;
        }
    }

    public double getAdaptiveMultiplier() {
        return cachedAdaptiveMultiplier;
    }

    public boolean isServerLagging() {
        return cachedServerLagging;
    }

    public int processEvent(UUID worldId, long chunkKey, long blockKey, boolean hasNearbyPlayers) {
        long now = System.currentTimeMillis();
        PluginConfig cfg = configManager.getPluginConfig();
        PluginConfig.RemovalAction action = cfg.getRemovalAction();

        if (action != PluginConfig.RemovalAction.NOTIFY) {
            synchronized (freezeLock) {
                Long2LongOpenHashMap worldFrozen = frozenBlocks.get(worldId);
                if (worldFrozen != null) {
                    long expiration = worldFrozen.get(blockKey);
                    if (expiration > 0) {
                        if (now < expiration) {
                            return EVENT_FREEZE;
                        }
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

            double multiplier = cachedAdaptiveMultiplier;
            if (!hasNearbyPlayers && cfg.getProximity().isEnabled()) {
                multiplier *= cfg.getProximity().getUnattendedStrictMultiplier();
            }

            if (cfg.getFingerprint().isEnabled()) {
                Long2LongOpenHashMap pTimes = lastPulseTimes.computeIfAbsent(worldId, k -> new Long2LongOpenHashMap());
                Long2LongOpenHashMap pDeltas = lastPulseDeltas.computeIfAbsent(worldId, k -> new Long2LongOpenHashMap());
                long lastTime = pTimes.get(blockKey);
                if (lastTime > 0) {
                    long delta = now - lastTime;
                    long prevDelta = pDeltas.get(blockKey);
                    if (prevDelta > 0 && Math.abs(delta - prevDelta) <= 50 && delta <= 300) {
                        multiplier *= cfg.getFingerprint().getClockStrictness();
                    }
                    pDeltas.put(blockKey, delta);
                }
                pTimes.put(blockKey, now);
            }

            int chunkThreshold = (int) Math.max(1, configManager.getChunkThreshold() * multiplier);
            int blockThreshold = (int) Math.max(1, configManager.getBlockThreshold() * multiplier);

            if (chunkVal > chunkThreshold && blockVal > blockThreshold) {
                int sustained;
                synchronized (freezeLock) {
                    Long2IntOpenHashMap sMap = sustainedViolations.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap());
                    sustained = sMap.addTo(blockKey, 1) + 1;
                }

                if (sustained < 2 && blockVal < blockThreshold * 3) {
                    return EVENT_NONE;
                }

                if (action == PluginConfig.RemovalAction.NOTIFY) {
                    return EVENT_NOTIFY;
                }

                if (action == PluginConfig.RemovalAction.FREEZE && cfg.getFreeze().isEnabled()) {
                    int duration = cfg.getFreeze().getDurationSeconds();
                    int maxAttempts = cfg.getFreeze().getMaxFreezeAttempts();
                    long decayWindowMs = Math.max(60000L, (long) duration * maxAttempts * 4000L);
                    int violations;
                    synchronized (freezeLock) {
                        Long2LongOpenHashMap expMap = freezeViolationExpiry.computeIfAbsent(worldId, k -> new Long2LongOpenHashMap());
                        Long2IntOpenHashMap vMap = freezeViolations.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap());
                        long prevExpiry = expMap.get(blockKey);
                        if (prevExpiry > 0 && now > prevExpiry) {
                            vMap.remove(blockKey);
                        }
                        violations = vMap.addTo(blockKey, 1) + 1;
                        expMap.put(blockKey, now + decayWindowMs);
                    }
                    if (violations <= maxAttempts) {
                        freezeBlock(worldId, blockKey, duration);
                        return EVENT_FREEZE;
                    }
                }
                return EVENT_DISABLE;
            }

            if (action != PluginConfig.RemovalAction.NOTIFY && configManager.isWarningEnabled()) {
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

    public void clearActiveBlocksAndLockdowns() {
        synchronized (freezeLock) {
            frozenBlocks.clear();
            freezeViolations.clear();
            freezeViolationExpiry.clear();
            sustainedViolations.clear();
        }
        synchronized (lockdownLock) {
            chunkLockdowns.clear();
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

            World world = Bukkit.getWorld(worldId);
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

                if (totalActivity <= 0) continue;

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
        if (location == null || location.getWorld() == null || ownerUuid == null) return;
        if (!configManager.isAlertsEnabled()) return;
        if (configManager.isOnlyWhenLagging() && !FOLIA && !cachedServerLagging) return;

        long now = System.currentTimeMillis();
        Long lastWarned = warnedPlayers.get(ownerUuid);
        if (lastWarned != null && now - lastWarned < WARNING_COOLDOWN_MS) return;

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner == null || !owner.isOnline()) return;

        long chunkKey = packChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        int currentCount = getChunkUpdates(location.getWorld().getUID(), chunkKey);
        int threshold = configManager.getChunkThreshold();
        int percent = threshold > 0 ? (currentCount * 100) / threshold : 0;

        String raw = messageManager.getMessagesConfig().getAlerts().getRedstoneWarning();
        Component msg = messageManager.parseMessage(raw)
                .replaceText(t -> t.matchLiteral("{x}").replacement(String.valueOf(location.getBlockX())))
                .replaceText(t -> t.matchLiteral("{y}").replacement(String.valueOf(location.getBlockY())))
                .replaceText(t -> t.matchLiteral("{z}").replacement(String.valueOf(location.getBlockZ())))
                .replaceText(t -> t.matchLiteral("{world}").replacement(location.getWorld().getName()))
                .replaceText(t -> t.matchLiteral("{material}").replacement(material.toString()))
                .replaceText(t -> t.matchLiteral("{percent}").replacement(String.valueOf(percent)))
                .replaceText(t -> t.matchLiteral("{current}").replacement(String.valueOf(currentCount)))
                .replaceText(t -> t.matchLiteral("{threshold}").replacement(String.valueOf(threshold)));

        if (messageManager.sendIfNotBlank(owner, raw, msg)) {
            warnedPlayers.put(ownerUuid, now);
        }
    }

    public boolean canSendAlert() {
        if (!configManager.isAlertsEnabled()) return false;
        if (configManager.isOnlyWhenLagging() && !FOLIA && !cachedServerLagging) return false;
        return tryCooldown(lastAlertTime, ALERT_COOLDOWN_MS);
    }

    private boolean canSendNotify() {
        if (!configManager.isAlertsEnabled()) return false;
        if (configManager.isOnlyWhenLagging() && !FOLIA && !cachedServerLagging) return false;
        return tryCooldown(lastNotifyTime, NOTIFY_COOLDOWN_MS);
    }

    private boolean tryCooldown(AtomicLong stamp, long cooldownMs) {
        long now = System.currentTimeMillis();
        long prev = stamp.get();
        if (now - prev >= cooldownMs) {
            return stamp.compareAndSet(prev, now);
        }
        return false;
    }

    public void resetCounters() {
        updatePerformanceSnapshot();

        if (configManager.isLogPerformance()) {
            logPerformanceStats();
        }

        checkLagAndNotify();

        synchronized (counterLock) {
            chunkCounters.values().forEach(Long2IntOpenHashMap::clear);
            blockCounters.values().forEach(Long2IntOpenHashMap::clear);
        }
        synchronized (freezeLock) {
            sustainedViolations.values().forEach(Long2IntOpenHashMap::clear);
        }

        long now = System.currentTimeMillis();
        if (now - lastPruneTime >= PRUNE_INTERVAL_MS) {
            lastPruneTime = now;
            pruneExpiredState(now);
        }

        if (now - lastResetTime > DAY_MS) {
            removalsToday.set(0);
            synchronized (freezeLock) {
                freezeViolations.values().forEach(Long2IntOpenHashMap::clear);
                freezeViolationExpiry.values().forEach(Long2LongOpenHashMap::clear);
            }
            lastResetTime = now;
            saveStatsAsync();
        }
    }

    private void pruneExpiredState(long now) {
        warnedPlayers.entrySet().removeIf(e -> now - e.getValue() >= WARNING_COOLDOWN_MS);

        synchronized (counterLock) {
            long pulseCutoff = now - PULSE_RETENTION_MS;
            for (Map.Entry<UUID, Long2LongOpenHashMap> entry : lastPulseTimes.entrySet()) {
                Long2LongOpenHashMap pTimes = entry.getValue();
                Long2LongOpenHashMap pDeltas = lastPulseDeltas.get(entry.getKey());
                Iterator<Long2LongMap.Entry> it = pTimes.long2LongEntrySet().iterator();
                while (it.hasNext()) {
                    Long2LongMap.Entry e = it.next();
                    if (e.getLongValue() < pulseCutoff) {
                        long k = e.getLongKey();
                        it.remove();
                        if (pDeltas != null) pDeltas.remove(k);
                    }
                }
            }
        }

        synchronized (freezeLock) {
            pruneExpiredLongMap(frozenBlocks, now);
            for (Map.Entry<UUID, Long2LongOpenHashMap> entry : freezeViolationExpiry.entrySet()) {
                Long2LongOpenHashMap expMap = entry.getValue();
                Long2IntOpenHashMap vMap = freezeViolations.get(entry.getKey());
                Iterator<Long2LongMap.Entry> it = expMap.long2LongEntrySet().iterator();
                while (it.hasNext()) {
                    Long2LongMap.Entry e = it.next();
                    if (e.getLongValue() < now) {
                        long k = e.getLongKey();
                        it.remove();
                        if (vMap != null) vMap.remove(k);
                    }
                }
            }
        }

        synchronized (lockdownLock) {
            pruneExpiredLongMap(chunkLockdowns, now);
        }
    }

    private void pruneExpiredLongMap(Map<UUID, Long2LongOpenHashMap> map, long now) {
        for (Long2LongOpenHashMap inner : map.values()) {
            Iterator<Long2LongMap.Entry> it = inner.long2LongEntrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getLongValue() < now) {
                    it.remove();
                }
            }
        }
    }

    private void checkLagAndNotify() {
        if (FOLIA || !cachedServerLagging) return;
        if (!configManager.isRedstoneEnabled() || !configManager.isAlertsEnabled() || !configManager.isNotifyOnTpsLag()) return;
        if (!tryCooldown(lastLagAlertTime, NOTIFY_COOLDOWN_MS)) return;

        UUID topWorldId = null;
        long topChunkKey = 0;
        int topChunkUps = 0;
        long topBlockKey = 0;
        int topBlockUps = 0;

        synchronized (counterLock) {
            for (Map.Entry<UUID, Long2IntOpenHashMap> e : chunkCounters.entrySet()) {
                for (var cEntry : e.getValue().long2IntEntrySet()) {
                    if (cEntry.getIntValue() > topChunkUps) {
                        topChunkUps = cEntry.getIntValue();
                        topChunkKey = cEntry.getLongKey();
                        topWorldId = e.getKey();
                    }
                }
            }

            if (topWorldId != null) {
                int targetChunkX = (int) (topChunkKey >> 32);
                int targetChunkZ = (int) topChunkKey;
                Long2IntOpenHashMap bMap = blockCounters.get(topWorldId);
                if (bMap != null) {
                    for (var bEntry : bMap.long2IntEntrySet()) {
                        long bk = bEntry.getLongKey();
                        if ((unpackBlockX(bk) >> 4) == targetChunkX && (unpackBlockZ(bk) >> 4) == targetChunkZ) {
                            if (bEntry.getIntValue() > topBlockUps) {
                                topBlockUps = bEntry.getIntValue();
                                topBlockKey = bk;
                            }
                        }
                    }
                }
            }
        }

        int minChunkInterest = Math.max(10, configManager.getChunkWarningThreshold());
        int minBlockInterest = Math.max(5, configManager.getBlockWarningThreshold());
        if (topWorldId == null || (topChunkUps < minChunkInterest && topBlockUps < minBlockInterest)) {
            return;
        }

        World world = Bukkit.getWorld(topWorldId);
        if (world == null) return;

        int bx = topBlockUps > 0 ? unpackBlockX(topBlockKey) : ((int) (topChunkKey >> 32)) * 16 + 8;
        int by = topBlockUps > 0 ? unpackBlockY(topBlockKey) : 64;
        int bz = topBlockUps > 0 ? unpackBlockZ(topBlockKey) : ((int) topChunkKey) * 16 + 8;
        Location loc = new Location(world, bx, by, bz);

        final int finalChunkUps = topChunkUps;
        final int finalBlockUps = topBlockUps;
        final double tpsSnap = cachedTps;
        final double msptSnap = cachedMspt;
        final UUID ownerUuid = plugin.getRedstoneListener() != null
                ? plugin.getRedstoneListener().getBlockOwner(topWorldId, bx, by, bz)
                : null;

        Runnable dispatch = () -> {
            String culprit = resolveCulpritName(world, bx, by, bz, ownerUuid);
            String raw = messageManager.getMessagesConfig().getAlerts().getTpsLagCulprit();
            Component alert = messageManager.parseMessage(raw)
                    .replaceText(t -> t.matchLiteral("{tps}").replacement(String.format("%.1f", tpsSnap)))
                    .replaceText(t -> t.matchLiteral("{mspt}").replacement(String.format("%.1f", msptSnap)))
                    .replaceText(t -> t.matchLiteral("{x}").replacement(String.valueOf(bx)))
                    .replaceText(t -> t.matchLiteral("{y}").replacement(String.valueOf(by)))
                    .replaceText(t -> t.matchLiteral("{z}").replacement(String.valueOf(bz)))
                    .replaceText(t -> t.matchLiteral("{world}").replacement(world.getName()))
                    .replaceText(t -> t.matchLiteral("{chunk_count}").replacement(String.valueOf(finalChunkUps)))
                    .replaceText(t -> t.matchLiteral("{block_count}").replacement(String.valueOf(finalBlockUps)))
                    .replaceText(t -> t.matchLiteral("{culprit}").replacement(culprit));

            broadcastAlert(raw, alert);
            logManager.logToFile("TPS_LAG_CULPRIT",
                    String.format("TPS: %.1f | MSPT: %.1f | Chunk UPS: %d | Block UPS: %d | Culprit: %s",
                            tpsSnap, msptSnap, finalChunkUps, finalBlockUps, culprit),
                    loc);
        };

        try {
            Bukkit.getRegionScheduler().execute(plugin, loc, dispatch);
        } catch (Throwable t) {
            plugin.getScheduler().runTaskLater(dispatch, 1L);
        }
    }

    public void handleRedstoneNotify(Location location, Material material, long chunkKey, long blockKey, UUID ownerUuid) {
        if (location == null || location.getWorld() == null) return;
        if (!canSendNotify()) return;

        int chunkCount = getChunkUpdates(location.getWorld().getUID(), chunkKey);
        int blockCount = getBlockUpdates(location.getWorld().getUID(), blockKey);
        String culprit = resolveCulpritName(location.getWorld(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), ownerUuid);

        String raw = messageManager.getMessagesConfig().getAlerts().getRedstoneNotify();
        Component alert = messageManager.parseMessage(raw)
                .replaceText(t -> t.matchLiteral("{x}").replacement(String.valueOf(location.getBlockX())))
                .replaceText(t -> t.matchLiteral("{y}").replacement(String.valueOf(location.getBlockY())))
                .replaceText(t -> t.matchLiteral("{z}").replacement(String.valueOf(location.getBlockZ())))
                .replaceText(t -> t.matchLiteral("{world}").replacement(location.getWorld().getName()))
                .replaceText(t -> t.matchLiteral("{material}").replacement(material.toString()))
                .replaceText(t -> t.matchLiteral("{chunk_count}").replacement(String.valueOf(chunkCount)))
                .replaceText(t -> t.matchLiteral("{block_count}").replacement(String.valueOf(blockCount)))
                .replaceText(t -> t.matchLiteral("{culprit}").replacement(culprit));

        broadcastAlert(raw, alert);
        logManager.logToFile("REDSTONE_NOTIFY",
                String.format("Notify | Material: %s | Chunk Updates: %d | Block Updates: %d | Culprit: %s",
                        material, chunkCount, blockCount, culprit),
                location);
    }

    public String resolveCulpritName(World world, int x, int y, int z, UUID ownerUuid) {
        if (ownerUuid != null) {
            Player online = Bukkit.getPlayer(ownerUuid);
            if (online != null) return online.getName();
            OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerUuid);
            if (offline.getName() != null) return offline.getName();
        }
        if (world == null) return "Unknown";
        Location loc = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        Player nearest = null;
        double nearestDistSq = 48.0 * 48.0;
        try {
            Collection<Player> nearby = world.getNearbyPlayers(loc, 48.0, 48.0, 48.0);
            for (Player player : nearby) {
                Location pLoc = player.getLocation();
                double dx = pLoc.getX() - loc.getX();
                double dy = pLoc.getY() - loc.getY();
                double dz = pLoc.getZ() - loc.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = player;
                }
            }
        } catch (Throwable ignored) {
        }
        return nearest != null ? nearest.getName() + " (Nearby)" : "Unknown";
    }

    public void handleRedstoneRemoval(Location location, Material material, long chunkKey, long blockKey) {
        if (location == null || location.getWorld() == null) return;
        int chunkCount = getChunkUpdates(location.getWorld().getUID(), chunkKey);
        int blockCount = getBlockUpdates(location.getWorld().getUID(), blockKey);

        totalRemovals.incrementAndGet();
        removalsToday.incrementAndGet();

        logManager.logRedstoneRemoval(location, material, chunkCount, blockCount, "Exceeded thresholds");

        if (canSendAlert()) {
            sendAlert(location, material, chunkCount, blockCount);
        }
    }

    private void sendAlert(Location location, Material material, int chunkCount, int blockCount) {
        if (location.getWorld() == null) return;
        String raw = messageManager.getMessagesConfig().getAlerts().getRedstoneRemoved();
        Component alertComponent = messageManager.parseMessage(raw)
                .replaceText(t -> t.matchLiteral("{x}").replacement(String.valueOf(location.getBlockX())))
                .replaceText(t -> t.matchLiteral("{y}").replacement(String.valueOf(location.getBlockY())))
                .replaceText(t -> t.matchLiteral("{z}").replacement(String.valueOf(location.getBlockZ())))
                .replaceText(t -> t.matchLiteral("{world}").replacement(location.getWorld().getName()))
                .replaceText(t -> t.matchLiteral("{material}").replacement(material.toString()))
                .replaceText(t -> t.matchLiteral("{chunk_count}").replacement(String.valueOf(chunkCount)))
                .replaceText(t -> t.matchLiteral("{block_count}").replacement(String.valueOf(blockCount)));

        broadcastAlert(raw, alertComponent);
    }

    private void broadcastAlert(String rawTemplate, Component alertComponent) {
        if (rawTemplate == null || rawTemplate.isBlank()) return;
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

    public double getCachedTps() {
        return cachedTps;
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

    public void saveStatsAsync() {
        final int total = totalRemovals.get();
        final int today = removalsToday.get();
        final long reset = lastResetTime;
        plugin.getScheduler().runTaskAsynchronously(() -> writeStatsFile(total, today, reset));
    }

    public void saveStats() {
        writeStatsFile(totalRemovals.get(), removalsToday.get(), lastResetTime);
    }

    private synchronized void writeStatsFile(int total, int today, long reset) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("total-removals", total);
            config.set("removals-today", today);
            config.set("last-reset-time", reset);
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

    public static int unpackBlockX(long key) {
        return (int) (key >> 38);
    }

    public static int unpackBlockZ(long key) {
        return (int) ((key << 26) >> 38);
    }

    public static int unpackBlockY(long key) {
        return (int) ((key << 52) >> 52);
    }
}