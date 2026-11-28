package org.ayosynk.antiRedstoneLag.engine;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.kyori.adventure.text.Component;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.ayosynk.antiRedstoneLag.diagnostics.SnapshotService;
import org.ayosynk.antiRedstoneLag.integration.WebhookService;
import org.ayosynk.antiRedstoneLag.manager.LogManager;
import org.ayosynk.antiRedstoneLag.storage.PluginStats;
import org.ayosynk.antiRedstoneLag.storage.StorageService;
import org.ayosynk.antiRedstoneLag.util.BlockKeyCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core redstone monitoring, fingerprinting, freeze/lockdown state machine, and persistent owner cache.
 */
public class RedstoneEngine {
    public static final int EVENT_NONE = 0;
    public static final int EVENT_WARN = 1;
    public static final int EVENT_DISABLE = 2;
    public static final int EVENT_FREEZE = 3;
    public static final int EVENT_NOTIFY = 4;

    private static final long ALERT_COOLDOWN_MS = 5_000L;
    private static final long NOTIFY_COOLDOWN_MS = 15_000L;
    private static final long WARNING_COOLDOWN_MS = 15_000L;
    private static final long PRUNE_INTERVAL_MS = 15_000L;
    private static final long BYPASS_CACHE_TTL_MS = 30_000L;

    public record FrozenBlockInfo(
            UUID worldId,
            String worldName,
            long chunkKey,
            long blockKey,
            int x,
            int y,
            int z,
            Material material,
            UUID ownerUuid,
            long expiresAtMs
    ) {
        public long remainingSeconds() {
            return Math.max(0L, (expiresAtMs - System.currentTimeMillis() + 999L) / 1000L);
        }
    }

    public record LockedChunkInfo(
            UUID worldId,
            String worldName,
            long chunkKey,
            int chunkX,
            int chunkZ,
            long expiresAtMs
    ) {
        public long remainingSeconds() {
            return Math.max(0L, (expiresAtMs - System.currentTimeMillis() + 999L) / 1000L);
        }
    }

    private record BypassEntry(boolean allowed, long expiresAtMs) {
    }

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;
    private final StorageService storageService;
    private final PluginStats stats;
    private final AdaptiveMonitor adaptiveMonitor;
    private final ClockFingerprinter fingerprinter;
    private final WebhookService webhookService;
    private volatile SnapshotService snapshotService;

    private final Map<UUID, ConcurrentHashMap<Long, Long2ObjectOpenHashMap<UUID>>> blockOwners = new ConcurrentHashMap<>();
    private final Map<UUID, BypassEntry> bypassCache = new ConcurrentHashMap<>();
    private final Map<UUID, ConcurrentHashMap<Long, LongOpenHashSet>> disabledBlocks = new ConcurrentHashMap<>();

    private final Map<UUID, Long2ObjectOpenHashMap<FrozenBlockInfo>> frozenBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Long2LongOpenHashMap> chunkLockdowns = new ConcurrentHashMap<>();
    private final Map<UUID, String> worldNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> freezeViolations = new ConcurrentHashMap<>();
    private final Map<UUID, Long2LongOpenHashMap> freezeViolationExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> sustainedViolations = new ConcurrentHashMap<>();

    private final Map<UUID, Long2IntOpenHashMap> chunkCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> lastIntervalChunkCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> blockCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> lastIntervalBlockCounters = new ConcurrentHashMap<>();

    private final Map<UUID, Long2IntOpenHashMap> pistonCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> hopperCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Long2IntOpenHashMap> crafterCounters = new ConcurrentHashMap<>();

    private final Map<UUID, Long> warnedPlayers = new ConcurrentHashMap<>();

    private final Object counterLock = new Object();
    private final Object freezeLock = new Object();
    private final Object lockdownLock = new Object();

    private final AtomicLong lastAlertTime = new AtomicLong(0);
    private final AtomicLong lastNotifyTime = new AtomicLong(0);
    private final AtomicLong lastLagAlertTime = new AtomicLong(0);
    private volatile long lastPruneTime = System.currentTimeMillis();

    public RedstoneEngine(AntiRedstoneLag plugin,
                          ConfigManager configManager,
                          MessageManager messageManager,
                          LogManager logManager,
                          StorageService storageService,
                          PluginStats stats,
                          WebhookService webhookService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
        this.storageService = storageService;
        this.stats = stats;
        this.webhookService = webhookService;
        this.adaptiveMonitor = new AdaptiveMonitor(configManager);
        this.fingerprinter = new ClockFingerprinter();
    }

    public void setSnapshotService(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    public AdaptiveMonitor getAdaptiveMonitor() {
        return adaptiveMonitor;
    }

    public PluginStats getStats() {
        return stats;
    }

    public int processRedstoneEvent(World world, long chunkKey, long blockKey, Material material, boolean hasNearbyPlayers) {
        long now = System.currentTimeMillis();
        UUID worldId = world.getUID();
        String worldName = world.getName();
        worldNames.put(worldId, worldName);

        PluginConfig cfg = configManager.getPluginConfig();
        WorldRuleSet rules = WorldRuleSet.resolve(configManager, worldName);
        PluginConfig.RemovalAction action = rules.removalAction();

        if (action != PluginConfig.RemovalAction.NOTIFY) {
            synchronized (freezeLock) {
                Long2ObjectOpenHashMap<FrozenBlockInfo> worldFrozen = frozenBlocks.get(worldId);
                if (worldFrozen != null) {
                    FrozenBlockInfo info = worldFrozen.get(blockKey);
                    if (info != null) {
                        if (now < info.expiresAtMs()) {
                            return EVENT_FREEZE;
                        }
                        worldFrozen.remove(blockKey);
                        notifyClockResumed(info);
                    }
                }
            }
        }

        int chunkVal;
        int blockVal;
        synchronized (counterLock) {
            chunkVal = chunkCounters.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap()).addTo(chunkKey, 1) + 1;
            blockVal = blockCounters.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap()).addTo(blockKey, 1) + 1;
        }

        double multiplier = adaptiveMonitor.getAdaptiveMultiplier();
        if (!hasNearbyPlayers && cfg.getProximity().isEnabled()) {
            multiplier *= cfg.getProximity().getUnattendedStrictMultiplier();
        }

        if (cfg.getFingerprint().isEnabled()) {
            var fp = cfg.getFingerprint();
            if (fingerprinter.recordAndCheckPeriodic(worldId, blockKey, now,
                    fp.getHistorySize(), fp.getMaxIntervalMs(), fp.getVarianceToleranceMs())) {
                multiplier *= fp.getClockStrictness();
            }
        }
        if (!adaptiveMonitor.isServerLagging()) {
            multiplier = Math.max(0.55, multiplier);
        }

        int chunkThreshold = (int) Math.max(1, rules.chunkThreshold() * multiplier);
        int blockThreshold = (int) Math.max(1, rules.blockThreshold() * multiplier);

        if (chunkVal > chunkThreshold && blockVal > blockThreshold) {
            int prevChunkVal;
            int prevBlockVal;
            synchronized (counterLock) {
                Long2IntOpenHashMap prevChunks = lastIntervalChunkCounters.get(worldId);
                Long2IntOpenHashMap prevBlocks = lastIntervalBlockCounters.get(worldId);
                prevChunkVal = prevChunks != null ? prevChunks.get(chunkKey) : 0;
                prevBlockVal = prevBlocks != null ? prevBlocks.get(blockKey) : 0;
            }
            if (!isSustainedAcrossIntervals(chunkVal, blockVal, prevChunkVal, prevBlockVal, chunkThreshold, blockThreshold)) {
                return EVENT_NONE;
            }

            if (action == PluginConfig.RemovalAction.NOTIFY) {
                return EVENT_NOTIFY;
            }

            if (action == PluginConfig.RemovalAction.FREEZE && cfg.getFreeze().isEnabled()) {
                int duration = cfg.getFreeze().getDurationSeconds();
                int maxAttempts = cfg.getFreeze().getMaxFreezeAttempts();
                long decayWindowMs = Math.max(60_000L, (long) duration * maxAttempts * 4_000L);
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
                    UUID owner = getOwner(worldId, chunkKey, blockKey);
                    freezeBlock(worldId, worldName, chunkKey, blockKey, material, owner, duration);
                    stats.incrementTotalFreezes();
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

    public static boolean isSustainedAcrossIntervals(int chunkVal, int blockVal,
                                                      int prevChunkVal, int prevBlockVal,
                                                      int chunkThreshold, int blockThreshold) {
        // Require activity across two consecutive reset intervals unless the burst is extreme.
        boolean consecutive = prevChunkVal > (chunkThreshold / 2) && prevBlockVal > (blockThreshold / 2);
        boolean extremeBurst = chunkVal >= chunkThreshold * 2 || blockVal >= blockThreshold * 3;
        return consecutive || extremeBurst;
    }

    public boolean checkPistonCycleExceeded(UUID worldId, long blockKey) {
        int limit = configManager.getPluginConfig().getDetectors().getPistonLoopThreshold();
        synchronized (counterLock) {
            int count = pistonCounters.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap()).addTo(blockKey, 1) + 1;
            return count > limit;
        }
    }

    public boolean checkHopperTransferExceeded(UUID worldId, long blockKey) {
        var hopperCfg = configManager.getPluginConfig().getDetectors().getHopperLimiter();
        if (!hopperCfg.isEnabled()) return false;
        synchronized (counterLock) {
            int count = hopperCounters.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap()).addTo(blockKey, 1) + 1;
            return count > hopperCfg.getMaxTransfersPerInterval();
        }
    }

    public boolean checkCrafterCraftExceeded(UUID worldId, long blockKey) {
        var crafterCfg = configManager.getPluginConfig().getDetectors().getCrafterLimiter();
        if (!crafterCfg.isEnabled()) return false;
        synchronized (counterLock) {
            int count = crafterCounters.computeIfAbsent(worldId, k -> new Long2IntOpenHashMap()).addTo(blockKey, 1) + 1;
            return count > crafterCfg.getMaxCraftsPerInterval();
        }
    }

    public void freezeBlock(UUID worldId, String worldName, long chunkKey, long blockKey,
                            Material material, UUID ownerUuid, int durationSeconds) {
        long expiresAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        FrozenBlockInfo info = new FrozenBlockInfo(
                worldId,
                worldName != null ? worldName : resolveWorldName(worldId),
                chunkKey,
                blockKey,
                BlockKeyCodec.unpackBlockX(blockKey),
                BlockKeyCodec.unpackBlockY(blockKey),
                BlockKeyCodec.unpackBlockZ(blockKey),
                material != null ? material : Material.REDSTONE_WIRE,
                ownerUuid,
                expiresAt
        );
        synchronized (freezeLock) {
            frozenBlocks.computeIfAbsent(worldId, k -> new Long2ObjectOpenHashMap<>()).put(blockKey, info);
        }
    }

    public boolean isBlockFrozen(UUID worldId, long blockKey) {
        long now = System.currentTimeMillis();
        synchronized (freezeLock) {
            Long2ObjectOpenHashMap<FrozenBlockInfo> worldFrozen = frozenBlocks.get(worldId);
            if (worldFrozen == null) return false;
            FrozenBlockInfo info = worldFrozen.get(blockKey);
            if (info == null) return false;
            if (now > info.expiresAtMs()) {
                worldFrozen.remove(blockKey);
                notifyClockResumed(info);
                return false;
            }
            return true;
        }
    }

    public long getFreezeRemainingMillis(UUID worldId, long blockKey) {
        synchronized (freezeLock) {
            Long2ObjectOpenHashMap<FrozenBlockInfo> worldFrozen = frozenBlocks.get(worldId);
            if (worldFrozen == null) return 0L;
            FrozenBlockInfo info = worldFrozen.get(blockKey);
            if (info == null) return 0L;
            return Math.max(0L, info.expiresAtMs() - System.currentTimeMillis());
        }
    }

    public int unfreezeAll() {
        int removed = 0;
        List<FrozenBlockInfo> resumed = new ArrayList<>();
        synchronized (freezeLock) {
            for (Long2ObjectOpenHashMap<FrozenBlockInfo> map : frozenBlocks.values()) {
                removed += map.size();
                resumed.addAll(map.values());
                map.clear();
            }
            freezeViolations.values().forEach(Long2IntOpenHashMap::clear);
            freezeViolationExpiry.values().forEach(Long2LongOpenHashMap::clear);
            sustainedViolations.values().forEach(Long2IntOpenHashMap::clear);
        }
        clearDisabledBlocks();
        for (FrozenBlockInfo info : resumed) {
            notifyClockResumed(info);
        }
        return removed;
    }

    public int unfreezeChunk(UUID worldId, long chunkKey) {
        if (worldId == null) return 0;
        int removed = 0;
        List<FrozenBlockInfo> resumed = new ArrayList<>();
        synchronized (freezeLock) {
            Long2ObjectOpenHashMap<FrozenBlockInfo> map = frozenBlocks.get(worldId);
            if (map != null) {
                Iterator<FrozenBlockInfo> it = map.values().iterator();
                while (it.hasNext()) {
                    FrozenBlockInfo info = it.next();
                    if (info.chunkKey() == chunkKey) {
                        it.remove();
                        resumed.add(info);
                        removed++;
                    }
                }
            }
            Long2IntOpenHashMap vMap = freezeViolations.get(worldId);
            Long2LongOpenHashMap eMap = freezeViolationExpiry.get(worldId);
            for (FrozenBlockInfo info : resumed) {
                if (vMap != null) vMap.remove(info.blockKey());
                if (eMap != null) eMap.remove(info.blockKey());
            }
        }
        ConcurrentHashMap<Long, LongOpenHashSet> worldDisabled = disabledBlocks.get(worldId);
        if (worldDisabled != null) {
            worldDisabled.remove(chunkKey);
        }
        for (FrozenBlockInfo info : resumed) {
            notifyClockResumed(info);
        }
        return removed;
    }

    public boolean unfreezeBlock(UUID worldId, long blockKey) {
        FrozenBlockInfo removed;
        synchronized (freezeLock) {
            Long2ObjectOpenHashMap<FrozenBlockInfo> map = frozenBlocks.get(worldId);
            if (map == null) return false;
            removed = map.remove(blockKey);
            Long2IntOpenHashMap vMap = freezeViolations.get(worldId);
            if (vMap != null) vMap.remove(blockKey);
            Long2LongOpenHashMap eMap = freezeViolationExpiry.get(worldId);
            if (eMap != null) eMap.remove(blockKey);
        }
        if (removed != null) {
            notifyClockResumed(removed);
            return true;
        }
        return false;
    }

    public List<FrozenBlockInfo> getActiveFreezes() {
        long now = System.currentTimeMillis();
        List<FrozenBlockInfo> list = new ArrayList<>();
        synchronized (freezeLock) {
            for (Long2ObjectOpenHashMap<FrozenBlockInfo> map : frozenBlocks.values()) {
                for (FrozenBlockInfo info : map.values()) {
                    if (info.expiresAtMs() > now) {
                        list.add(info);
                    }
                }
            }
        }
        return list;
    }

    public boolean lockdownChunk(UUID worldId, String worldName, long chunkKey, long durationSeconds) {
        if (worldName != null) {
            worldNames.put(worldId, worldName);
        }
        long now = System.currentTimeMillis();
        synchronized (lockdownLock) {
            Long2LongOpenHashMap lockdowns = chunkLockdowns.computeIfAbsent(worldId, k -> new Long2LongOpenHashMap());
            if (lockdowns.containsKey(chunkKey) && lockdowns.get(chunkKey) > now) {
                return false;
            }
            lockdowns.put(chunkKey, now + (durationSeconds * 1000L));
        }
        stats.incrementTotalLockdowns();
        return true;
    }

    public boolean isChunkLocked(UUID worldId, long chunkKey) {
        long now = System.currentTimeMillis();
        synchronized (lockdownLock) {
            Long2LongOpenHashMap lockdowns = chunkLockdowns.get(worldId);
            if (lockdowns == null) return false;
            long expiration = lockdowns.get(chunkKey);
            if (expiration == 0L) return false;
            if (now > expiration) {
                lockdowns.remove(chunkKey);
                return false;
            }
            return true;
        }
    }

    public long getLockdownRemainingMillis(UUID worldId, long chunkKey) {
        synchronized (lockdownLock) {
            Long2LongOpenHashMap lockdowns = chunkLockdowns.get(worldId);
            if (lockdowns == null) return 0L;
            long expiration = lockdowns.get(chunkKey);
            if (expiration == 0L) return 0L;
            return Math.max(0L, expiration - System.currentTimeMillis());
        }
    }

    public int unlockAll() {
        int count = 0;
        synchronized (lockdownLock) {
            for (Long2LongOpenHashMap map : chunkLockdowns.values()) {
                count += map.size();
                map.clear();
            }
        }
        return count;
    }

    public boolean unlockChunk(UUID worldId, long chunkKey) {
        synchronized (lockdownLock) {
            Long2LongOpenHashMap map = chunkLockdowns.get(worldId);
            if (map == null) return false;
            return map.remove(chunkKey) != 0L;
        }
    }

    public List<LockedChunkInfo> getActiveLockdowns() {
        long now = System.currentTimeMillis();
        List<LockedChunkInfo> list = new ArrayList<>();
        synchronized (lockdownLock) {
            for (Map.Entry<UUID, Long2LongOpenHashMap> e : chunkLockdowns.entrySet()) {
                UUID worldId = e.getKey();
                String worldName = resolveWorldName(worldId);
                for (Long2LongMap.Entry entry : e.getValue().long2LongEntrySet()) {
                    if (entry.getLongValue() > now) {
                        long ck = entry.getLongKey();
                        list.add(new LockedChunkInfo(
                                worldId, worldName, ck,
                                BlockKeyCodec.unpackChunkX(ck),
                                BlockKeyCodec.unpackChunkZ(ck),
                                entry.getLongValue()));
                    }
                }
            }
        }
        return list;
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
        clearDisabledBlocks();
        fingerprinter.clearAll();
    }

    public void markBlockDisabled(UUID worldId, long chunkKey, long blockKey) {
        LongOpenHashSet chunkDisabled = disabledBlocks
                .computeIfAbsent(worldId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, k -> new LongOpenHashSet());
        synchronized (chunkDisabled) {
            chunkDisabled.add(blockKey);
        }
    }

    public boolean isBlockDisabled(UUID worldId, long chunkKey, long blockKey) {
        ConcurrentHashMap<Long, LongOpenHashSet> worldDisabled = disabledBlocks.get(worldId);
        if (worldDisabled == null) return false;
        LongOpenHashSet chunkDisabled = worldDisabled.get(chunkKey);
        if (chunkDisabled == null) return false;
        synchronized (chunkDisabled) {
            return chunkDisabled.contains(blockKey);
        }
    }

    public void clearDisabledBlocks() {
        for (ConcurrentHashMap<Long, LongOpenHashSet> worldMap : disabledBlocks.values()) {
            worldMap.clear();
        }
    }

    public void setBlockOwner(UUID worldId, long chunkKey, long blockKey, UUID ownerUuid) {
        if (worldId == null || ownerUuid == null) return;
        Long2ObjectOpenHashMap<UUID> chunkMap = blockOwners
                .computeIfAbsent(worldId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, k -> new Long2ObjectOpenHashMap<>());
        synchronized (chunkMap) {
            chunkMap.put(blockKey, ownerUuid);
        }
        if (configManager.getPluginConfig().getStorage().isPersistOwners()) {
            storageService.saveBlockOwnerAsync(worldId, chunkKey, blockKey, ownerUuid);
        }
    }

    public UUID getOwner(UUID worldId, long chunkKey, long blockKey) {
        ConcurrentHashMap<Long, Long2ObjectOpenHashMap<UUID>> worldOwners = blockOwners.get(worldId);
        if (worldOwners != null) {
            Long2ObjectOpenHashMap<UUID> chunkMap = worldOwners.get(chunkKey);
            if (chunkMap != null) {
                synchronized (chunkMap) {
                    return chunkMap.get(blockKey);
                }
            }
        }
        return null;
    }

    public void removeBlockOwner(UUID worldId, long chunkKey, long blockKey) {
        ConcurrentHashMap<Long, Long2ObjectOpenHashMap<UUID>> worldOwners = blockOwners.get(worldId);
        if (worldOwners != null) {
            Long2ObjectOpenHashMap<UUID> chunkMap = worldOwners.get(chunkKey);
            if (chunkMap != null) {
                synchronized (chunkMap) {
                    chunkMap.remove(blockKey);
                    if (chunkMap.isEmpty()) {
                        worldOwners.remove(chunkKey, chunkMap);
                    }
                }
            }
        }
        if (configManager.getPluginConfig().getStorage().isPersistOwners()) {
            storageService.removeBlockOwnerAsync(worldId, chunkKey, blockKey);
        }
    }

    public void hydrateChunkOwners(UUID worldId, long chunkKey) {
        if (!configManager.getPluginConfig().getStorage().isPersistOwners()) return;
        storageService.loadChunkOwnersAsync(worldId, chunkKey).thenAccept(loaded -> {
            if (loaded == null || loaded.isEmpty()) return;
            Long2ObjectOpenHashMap<UUID> chunkMap = blockOwners
                    .computeIfAbsent(worldId, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(chunkKey, k -> new Long2ObjectOpenHashMap<>());
            synchronized (chunkMap) {
                for (Map.Entry<Long, UUID> entry : loaded.entrySet()) {
                    chunkMap.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        });
    }

    public void unloadChunkCache(UUID worldId, long chunkKey) {
        ConcurrentHashMap<Long, Long2ObjectOpenHashMap<UUID>> worldOwners = blockOwners.get(worldId);
        if (worldOwners != null) {
            worldOwners.remove(chunkKey);
        }
    }

    public void cachePlayerBypass(UUID playerUuid, boolean hasBypass) {
        if (playerUuid == null) return;
        bypassCache.put(playerUuid, new BypassEntry(hasBypass, System.currentTimeMillis() + BYPASS_CACHE_TTL_MS));
    }

    public boolean hasOwnerBypass(UUID worldId, long chunkKey, long blockKey) {
        UUID ownerUuid = getOwner(worldId, chunkKey, blockKey);
        if (ownerUuid == null) return false;

        Player online = Bukkit.getPlayer(ownerUuid);
        if (online != null) {
            boolean allowed = online.hasPermission("antiredstonelag.bypass");
            cachePlayerBypass(ownerUuid, allowed);
            return allowed;
        }
        BypassEntry cached = bypassCache.get(ownerUuid);
        return cached != null && cached.allowed() && cached.expiresAtMs() > System.currentTimeMillis();
    }

    public void resetIntervalCounters() {
        adaptiveMonitor.updateSnapshot();

        if (configManager.isLogPerformance()) {
            logPerformanceStats();
        }

        checkLagAndNotify();

        synchronized (counterLock) {
            lastIntervalChunkCounters.clear();
            for (Map.Entry<UUID, Long2IntOpenHashMap> e : chunkCounters.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    lastIntervalChunkCounters.put(e.getKey(), new Long2IntOpenHashMap(e.getValue()));
                }
            }
            lastIntervalBlockCounters.clear();
            for (Map.Entry<UUID, Long2IntOpenHashMap> e : blockCounters.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    lastIntervalBlockCounters.put(e.getKey(), new Long2IntOpenHashMap(e.getValue()));
                }
            }

            chunkCounters.values().forEach(Long2IntOpenHashMap::clear);
            blockCounters.values().forEach(Long2IntOpenHashMap::clear);
            pistonCounters.values().forEach(Long2IntOpenHashMap::clear);
            hopperCounters.values().forEach(Long2IntOpenHashMap::clear);
            crafterCounters.values().forEach(Long2IntOpenHashMap::clear);
        }

        synchronized (freezeLock) {
            sustainedViolations.values().forEach(Long2IntOpenHashMap::clear);
        }

        clearDisabledBlocks();

        long now = System.currentTimeMillis();
        if (now - lastPruneTime >= PRUNE_INTERVAL_MS) {
            lastPruneTime = now;
            pruneExpiredState(now);
        }

        if (stats.rolloverIfNeeded(now)) {
            synchronized (freezeLock) {
                freezeViolations.values().forEach(Long2IntOpenHashMap::clear);
                freezeViolationExpiry.values().forEach(Long2LongOpenHashMap::clear);
            }
            storageService.saveStatsAsync(stats);
        }
    }

    private void pruneExpiredState(long now) {
        warnedPlayers.entrySet().removeIf(e -> now - e.getValue() >= WARNING_COOLDOWN_MS);
        bypassCache.entrySet().removeIf(e -> e.getValue().expiresAtMs() < now);
        fingerprinter.pruneExpired(now);

        List<FrozenBlockInfo> expiredFreezes = new ArrayList<>();
        synchronized (freezeLock) {
            for (Long2ObjectOpenHashMap<FrozenBlockInfo> worldMap : frozenBlocks.values()) {
                Iterator<FrozenBlockInfo> it = worldMap.values().iterator();
                while (it.hasNext()) {
                    FrozenBlockInfo info = it.next();
                    if (info.expiresAtMs() <= now) {
                        it.remove();
                        expiredFreezes.add(info);
                    }
                }
            }
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
        for (FrozenBlockInfo info : expiredFreezes) {
            notifyClockResumed(info);
        }

        synchronized (lockdownLock) {
            for (Long2LongOpenHashMap inner : chunkLockdowns.values()) {
                Iterator<Long2LongMap.Entry> it = inner.long2LongEntrySet().iterator();
                while (it.hasNext()) {
                    if (it.next().getLongValue() < now) {
                        it.remove();
                    }
                }
            }
        }
    }

    private void notifyClockResumed(FrozenBlockInfo info) {
        if (!configManager.getPluginConfig().getFreeze().isNotifyOnResume()) return;
        if (!configManager.isAlertsEnabled() || info.ownerUuid() == null) return;
        Player owner = Bukkit.getPlayer(info.ownerUuid());
        if (owner == null || !owner.isOnline()) return;

        String raw = messageManager.getMessagesConfig().getAlerts().getClockResumed();
        messageManager.sendIfNotBlank(owner, raw, Map.of(
                "x", String.valueOf(info.x()),
                "y", String.valueOf(info.y()),
                "z", String.valueOf(info.z()),
                "world", info.worldName()
        ));
    }

    private void checkLagAndNotify() {
        if (AdaptiveMonitor.isFolia() || !adaptiveMonitor.isServerLagging()) return;
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
                int targetChunkX = BlockKeyCodec.unpackChunkX(topChunkKey);
                int targetChunkZ = BlockKeyCodec.unpackChunkZ(topChunkKey);
                Long2IntOpenHashMap bMap = blockCounters.get(topWorldId);
                if (bMap != null) {
                    for (var bEntry : bMap.long2IntEntrySet()) {
                        long bk = bEntry.getLongKey();
                        if ((BlockKeyCodec.unpackBlockX(bk) >> 4) == targetChunkX
                                && (BlockKeyCodec.unpackBlockZ(bk) >> 4) == targetChunkZ) {
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

        int bx = topBlockUps > 0 ? BlockKeyCodec.unpackBlockX(topBlockKey) : BlockKeyCodec.unpackChunkX(topChunkKey) * 16 + 8;
        int by = topBlockUps > 0 ? BlockKeyCodec.unpackBlockY(topBlockKey) : 64;
        int bz = topBlockUps > 0 ? BlockKeyCodec.unpackBlockZ(topBlockKey) : BlockKeyCodec.unpackChunkZ(topChunkKey) * 16 + 8;
        Location loc = new Location(world, bx, by, bz);

        final int finalChunkUps = topChunkUps;
        final int finalBlockUps = topBlockUps;
        final double tpsSnap = adaptiveMonitor.getCachedTps();
        final double msptSnap = adaptiveMonitor.getCachedMspt();
        final UUID ownerUuid = getOwner(topWorldId, topChunkKey, topBlockKey);

        plugin.getScheduler().runAtLocation(loc, () -> {
            String culprit = resolveCulpritName(world, bx, by, bz, ownerUuid);
            String raw = messageManager.getMessagesConfig().getAlerts().getTpsLagCulprit();
            Component alert = messageManager.parseMessage(raw, Map.of(
                    "tps", String.format("%.1f", tpsSnap),
                    "mspt", String.format("%.1f", msptSnap),
                    "x", String.valueOf(bx),
                    "y", String.valueOf(by),
                    "z", String.valueOf(bz),
                    "world", world.getName(),
                    "chunk_count", String.valueOf(finalChunkUps),
                    "block_count", String.valueOf(finalBlockUps),
                    "culprit", culprit
            ));
            broadcastAlert(raw, alert);
            logManager.logToFile("TPS_LAG_CULPRIT",
                    String.format("TPS: %.1f | MSPT: %.1f | Chunk UPS: %d | Block UPS: %d | Culprit: %s",
                            tpsSnap, msptSnap, finalChunkUps, finalBlockUps, culprit),
                    loc);
            if (webhookService != null) {
                webhookService.sendAlert("TPS_LAG", "Critical TPS Lag Detected",
                        String.format("TPS: %.1f | MSPT: %.1fms\nLocation: %d, %d, %d (%s)\nChunk UPS: %d | Block UPS: %d\nCulprit: %s",
                                tpsSnap, msptSnap, bx, by, bz, world.getName(), finalChunkUps, finalBlockUps, culprit));
            }
        });
    }

    public void sendOwnerWarning(Location location, Material material, UUID ownerUuid) {
        if (location == null || location.getWorld() == null || ownerUuid == null) return;
        if (!configManager.isAlertsEnabled()) return;
        if (configManager.isOnlyWhenLagging() && !AdaptiveMonitor.isFolia() && !adaptiveMonitor.isServerLagging()) return;

        long now = System.currentTimeMillis();
        Long lastWarned = warnedPlayers.get(ownerUuid);
        if (lastWarned != null && now - lastWarned < WARNING_COOLDOWN_MS) return;

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner == null || !owner.isOnline()) return;

        long chunkKey = BlockKeyCodec.packChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        int currentCount = getChunkUpdates(location.getWorld().getUID(), chunkKey);
        int threshold = configManager.getChunkThreshold(location.getWorld().getName());
        int percent = threshold > 0 ? (currentCount * 100) / threshold : 0;

        String raw = messageManager.getMessagesConfig().getAlerts().getRedstoneWarning();
        Component msg = messageManager.parseMessage(raw, Map.of(
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ()),
                "world", location.getWorld().getName(),
                "material", material.name(),
                "percent", String.valueOf(percent),
                "current", String.valueOf(currentCount),
                "threshold", String.valueOf(threshold)
        ));
        if (messageManager.sendIfNotBlank(owner, raw, msg)) {
            warnedPlayers.put(ownerUuid, now);
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
        Component alert = messageManager.parseMessage(raw, Map.of(
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ()),
                "world", location.getWorld().getName(),
                "material", material.name(),
                "chunk_count", String.valueOf(chunkCount),
                "block_count", String.valueOf(blockCount),
                "culprit", culprit
        ));
        broadcastAlert(raw, alert);
        logManager.logToFile("REDSTONE_NOTIFY",
                String.format("Notify | Material: %s | Chunk Updates: %d | Block Updates: %d | Culprit: %s",
                        material, chunkCount, blockCount, culprit),
                location);
    }

    public void handleRedstoneMitigation(Location location, Material material, long chunkKey, long blockKey, PluginConfig.RemovalAction action) {
        if (location == null || location.getWorld() == null) return;
        int chunkCount = getChunkUpdates(location.getWorld().getUID(), chunkKey);
        int blockCount = getBlockUpdates(location.getWorld().getUID(), blockKey);

        stats.incrementTotalRemovals();
        stats.incrementRemovalsToday();
        storageService.saveStatsAsync(stats);

        logManager.logRedstoneRemoval(location, material, chunkCount, blockCount, "Exceeded thresholds (" + action.name() + ")");

        if (canSendAlert()) {
            String raw = messageManager.getMessagesConfig().getAlerts().getRedstoneRemoved();
            Component alertComponent = messageManager.parseMessage(raw, Map.of(
                    "x", String.valueOf(location.getBlockX()),
                    "y", String.valueOf(location.getBlockY()),
                    "z", String.valueOf(location.getBlockZ()),
                    "world", location.getWorld().getName(),
                    "material", material.name(),
                    "action", action.name(),
                    "chunk_count", String.valueOf(chunkCount),
                    "block_count", String.valueOf(blockCount)
            ));
            broadcastAlert(raw, alertComponent);
        }

        if (webhookService != null) {
            webhookService.sendAlert("REMOVAL", "Lag Machine Mitigated",
                    String.format("Action: %s | Block: %s\nLocation: %d, %d, %d (%s)\nUpdates: %d chunk / %d block",
                            action.name(), material.name(),
                            location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                            location.getWorld().getName(), chunkCount, blockCount));
        }
    }

    public void broadcastAlert(String rawTemplate, Component alertComponent) {
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

    public boolean canSendAlert() {
        if (!configManager.isAlertsEnabled()) return false;
        if (configManager.isOnlyWhenLagging() && !AdaptiveMonitor.isFolia() && !adaptiveMonitor.isServerLagging()) return false;
        return tryCooldown(lastAlertTime, ALERT_COOLDOWN_MS);
    }

    private boolean canSendNotify() {
        if (!configManager.isAlertsEnabled()) return false;
        if (configManager.isOnlyWhenLagging() && !AdaptiveMonitor.isFolia() && !adaptiveMonitor.isServerLagging()) return false;
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

    public Map<UUID, Long2IntOpenHashMap> snapshotChunkActivity() {
        Map<UUID, Long2IntOpenHashMap> snapshot = new HashMap<>();
        synchronized (counterLock) {
            for (Map.Entry<UUID, Long2IntOpenHashMap> e : lastIntervalChunkCounters.entrySet()) {
                Long2IntOpenHashMap src = e.getValue();
                if (!src.isEmpty()) {
                    snapshot.put(e.getKey(), new Long2IntOpenHashMap(src));
                }
            }
            for (Map.Entry<UUID, Long2IntOpenHashMap> e : chunkCounters.entrySet()) {
                Long2IntOpenHashMap src = e.getValue();
                if (src.isEmpty()) continue;
                Long2IntOpenHashMap dst = snapshot.computeIfAbsent(e.getKey(), k -> new Long2IntOpenHashMap());
                for (var entry : src.long2IntEntrySet()) {
                    long k = entry.getLongKey();
                    int v = entry.getIntValue();
                    if (v > dst.get(k)) {
                        dst.put(k, v);
                    }
                }
            }
        }
        return snapshot;
    }

    public List<Long> getActiveBlockKeysNear(UUID worldId, int centerChunkX, int centerChunkZ, int chunkRadius) {
        List<Long> keys = new ArrayList<>();
        synchronized (counterLock) {
            Long2IntOpenHashMap curr = blockCounters.get(worldId);
            Long2IntOpenHashMap prev = lastIntervalBlockCounters.get(worldId);
            collectNear(curr, centerChunkX, centerChunkZ, chunkRadius, keys);
            if (keys.isEmpty()) {
                collectNear(prev, centerChunkX, centerChunkZ, chunkRadius, keys);
            }
        }
        return keys;
    }

    private static void collectNear(Long2IntOpenHashMap map, int ccx, int ccz, int r, List<Long> out) {
        if (map == null || map.isEmpty()) return;
        for (var entry : map.long2IntEntrySet()) {
            long bk = entry.getLongKey();
            int cx = BlockKeyCodec.unpackBlockX(bk) >> 4;
            int cz = BlockKeyCodec.unpackBlockZ(bk) >> 4;
            if (Math.abs(cx - ccx) <= r && Math.abs(cz - ccz) <= r) {
                out.add(bk);
            }
        }
    }

    public int getChunkUpdates(UUID worldId, long chunkKey) {
        synchronized (counterLock) {
            Long2IntOpenHashMap cMap = chunkCounters.get(worldId);
            return cMap != null ? cMap.get(chunkKey) : 0;
        }
    }

    public int getDisplayChunkUpdates(UUID worldId, long chunkKey) {
        synchronized (counterLock) {
            Long2IntOpenHashMap curr = chunkCounters.get(worldId);
            Long2IntOpenHashMap prev = lastIntervalChunkCounters.get(worldId);
            int currVal = curr != null ? curr.get(chunkKey) : 0;
            int prevVal = prev != null ? prev.get(chunkKey) : 0;
            return Math.max(currVal, prevVal);
        }
    }

    public int getBlockUpdates(UUID worldId, long blockKey) {
        synchronized (counterLock) {
            Long2IntOpenHashMap bMap = blockCounters.get(worldId);
            return bMap != null ? bMap.get(blockKey) : 0;
        }
    }

    public int getDisplayBlockUpdates(UUID worldId, long blockKey) {
        synchronized (counterLock) {
            Long2IntOpenHashMap curr = blockCounters.get(worldId);
            Long2IntOpenHashMap prev = lastIntervalBlockCounters.get(worldId);
            return Math.max(curr != null ? curr.get(blockKey) : 0, prev != null ? prev.get(blockKey) : 0);
        }
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

    public int getActiveFreezeCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        synchronized (freezeLock) {
            for (Long2ObjectOpenHashMap<FrozenBlockInfo> map : frozenBlocks.values()) {
                for (FrozenBlockInfo info : map.values()) {
                    if (info.expiresAtMs() > now) count++;
                }
            }
        }
        return count;
    }

    public int getActiveLockdownCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        synchronized (lockdownLock) {
            for (Long2LongOpenHashMap map : chunkLockdowns.values()) {
                for (long exp : map.values()) {
                    if (exp > now) count++;
                }
            }
        }
        return count;
    }

    public SnapshotService getSnapshotService() {
        return snapshotService;
    }

    public WebhookService getWebhookService() {
        return webhookService;
    }

    private void logPerformanceStats() {
        int chunksMonitored = 0;
        int blocksMonitored = 0;
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

    private String resolveWorldName(UUID worldId) {
        World world = Bukkit.getWorld(worldId);
        if (world != null) {
            worldNames.put(worldId, world.getName());
            return world.getName();
        }
        return worldNames.getOrDefault(worldId, worldId.toString());
    }
}
