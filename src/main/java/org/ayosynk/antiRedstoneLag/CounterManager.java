package org.ayosynk.antiRedstoneLag;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Manages redstone update counters per chunk and block.
 * Tracks statistics and handles alerts when thresholds are exceeded.
 */
public class CounterManager {
    private static final long ALERT_COOLDOWN_MS = 1000; // 1 second cooldown between alerts
    private static final long WARNING_COOLDOWN_MS = 5000; // 5 second cooldown between warnings
    private static final long DAY_MS = 24 * 60 * 60 * 1000;

    // Using fastutil primitive collections to reduce boxing overhead
    // Object2IntOpenHashMap stores int primitives directly, avoiding AtomicInteger allocation
    private final Object2IntOpenHashMap<String> chunkCounters = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<String> blockCounters = new Object2IntOpenHashMap<>();
    private final Map<String, Long> warnedPlayers = new ConcurrentHashMap<>(); // Track warned players
    private final Object counterLock = new Object(); // Lock for thread-safe counter access
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;
    private final File statsFile;

    // Statistics
    private final AtomicInteger totalRemovals = new AtomicInteger(0);
    private final AtomicInteger removalsToday = new AtomicInteger(0);
    private final AtomicLong lastAlertTime = new AtomicLong(0);
    private final AtomicLong lastWarningTime = new AtomicLong(0);
    private long lastResetTime = System.currentTimeMillis();

    public CounterManager(ConfigManager configManager, MessageManager messageManager, LogManager logManager, File dataFolder) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
        this.statsFile = new File(dataFolder, "stats.yml");
        loadStats();
    }

    /**
     * Increment counters using pre-computed keys (avoids object allocation).
     * Uses fastutil primitive int map to avoid boxing overhead.
     */
    public void incrementCounters(String chunkKey, String blockKey) {
        if (chunkKey == null || blockKey == null) return;
        synchronized (counterLock) {
            chunkCounters.addTo(chunkKey, 1);
            blockCounters.addTo(blockKey, 1);
        }
    }

    /**
     * Check if redstone should be disabled using pre-computed keys.
     */
    public boolean shouldDisable(String chunkKey, String blockKey) {
        if (chunkKey == null || blockKey == null) return false;
        synchronized (counterLock) {
            int chunkCount = chunkCounters.getInt(chunkKey);
            int blockCount = blockCounters.getInt(blockKey);
            return (chunkCount > configManager.getChunkThreshold()) &&
                    (blockCount > configManager.getBlockThreshold());
        }
    }

    /**
     * Check if warning should be sent (approaching threshold).
     */
    public boolean shouldWarn(String chunkKey, String blockKey) {
        if (!configManager.isWarningEnabled()) return false;
        if (chunkKey == null || blockKey == null) return false;

        synchronized (counterLock) {
            int chunkVal = chunkCounters.getInt(chunkKey);
            int blockVal = blockCounters.getInt(blockKey);

            return (chunkVal >= configManager.getChunkWarningThreshold() && chunkVal <= configManager.getChunkThreshold()) ||
                    (blockVal >= configManager.getBlockWarningThreshold() && blockVal <= configManager.getBlockThreshold());
        }
    }

    /**
     * Send warning to nearby players about approaching threshold.
     */
    public void sendWarning(Location location, Material material, java.util.UUID ownerUuid) {
        if (location == null || location.getWorld() == null) return;
        if (!canSendWarning()) return;

        // Get current counts for the warning message
        String chunkKey = location.getWorld().getName() + ":" + (location.getBlockX() >> 4) + ":" + (location.getBlockZ() >> 4);
        int currentCount;
        synchronized (counterLock) {
            currentCount = chunkCounters.getInt(chunkKey);
        }
        int threshold = configManager.getChunkThreshold();
        int percent = (currentCount * 100) / threshold;

        String warningMessage = messageManager.getMessage("alerts.redstone-warning",
                        "&#FFD93D⚠ &eWarning: &7Redstone activity at &e{x}, {y}, {z} &7is at &c{percent}% &7of threshold!")
                .replace("{x}", String.valueOf(location.getBlockX()))
                .replace("{y}", String.valueOf(location.getBlockY()))
                .replace("{z}", String.valueOf(location.getBlockZ()))
                .replace("{world}", location.getWorld().getName())
                .replace("{material}", material.toString())
                .replace("{percent}", String.valueOf(percent))
                .replace("{current}", String.valueOf(currentCount))
                .replace("{threshold}", String.valueOf(threshold));

        // Send to block owner if online
        if (ownerUuid != null) {
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (owner != null && owner.isOnline()) {
                // Check cooldown per player
                Long lastWarned = warnedPlayers.get(ownerUuid.toString());
                long now = System.currentTimeMillis();
                if (lastWarned == null || now - lastWarned >= WARNING_COOLDOWN_MS) {
                    owner.sendMessage(warningMessage);
                    warnedPlayers.put(ownerUuid.toString(), now);
                }
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
        // Log performance stats before resetting
        if (configManager.isLogPerformance()) {
            logPerformanceStats();
        }

        synchronized (counterLock) {
            chunkCounters.clear();
            blockCounters.clear();
        }

        // Reset daily counter if it's a new day
        if (System.currentTimeMillis() - lastResetTime > DAY_MS) {
            removalsToday.set(0);
            lastResetTime = System.currentTimeMillis();
            saveStats();
        }
    }

    public void handleRedstoneRemoval(Location location, Material material) {
        if (location == null || location.getWorld() == null) return;
        
        String chunkKey = location.getWorld().getName() + ":" + (location.getBlockX() >> 4) + ":" + (location.getBlockZ() >> 4);
        String blockKey = location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        int chunkCount, blockCount;
        synchronized (counterLock) {
            chunkCount = chunkCounters.getInt(chunkKey);
            blockCount = blockCounters.getInt(blockKey);
        }

        totalRemovals.incrementAndGet();
        removalsToday.incrementAndGet();

        // Log the removal
        logManager.logRedstoneRemoval(location, material, chunkCount, blockCount, "Exceeded thresholds");

        // Send alert if enabled (with cooldown to prevent spam)
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
        
        String alertMessage = messageManager.getMessage("alerts.redstone-removed")
                .replace("{x}", String.valueOf(location.getBlockX()))
                .replace("{y}", String.valueOf(location.getBlockY()))
                .replace("{z}", String.valueOf(location.getBlockZ()))
                .replace("{world}", location.getWorld().getName())
                .replace("{material}", material.toString())
                .replace("{chunk_count}", String.valueOf(chunkCount))
                .replace("{block_count}", String.valueOf(blockCount));

        // Split multi-line messages
        String[] lines = alertMessage.split("\n");

        // Send to all players with permission
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("antiredstonelag.alerts")) {
                player.sendMessage(lines);
            }
        }

        // Also log to console if enabled
        if (configManager.isLogToConsole()) {
            for (String line : lines) {
                Bukkit.getConsoleSender().sendMessage(line);
            }
        }
    }

    private void logPerformanceStats() {
        int chunksMonitored, blocksMonitored;
        double avgUpdates;
        synchronized (counterLock) {
            chunksMonitored = chunkCounters.size();
            blocksMonitored = blockCounters.size();
            avgUpdates = chunkCounters.values().intStream().average().orElse(0.0);
        }
        logManager.logPerformanceStats(chunksMonitored, blocksMonitored, avgUpdates);
    }

    // Statistics getters
    public int getTotalRemovals() {
        return totalRemovals.get();
    }

    public int getRemovalsToday() {
        return removalsToday.get();
    }

    public int getChunksMonitored() {
        synchronized (counterLock) {
            return chunkCounters.size();
        }
    }

    public int getBlocksMonitored() {
        synchronized (counterLock) {
            return blockCounters.size();
        }
    }


    /**
     * Load statistics from file.
     */
    private void loadStats() {
        if (!statsFile.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(statsFile);
            totalRemovals.set(config.getInt("total-removals", 0));
            removalsToday.set(config.getInt("removals-today", 0));
            lastResetTime = config.getLong("last-reset-time", System.currentTimeMillis());

            // Check if we need to reset daily counter
            if (System.currentTimeMillis() - lastResetTime > DAY_MS) {
                removalsToday.set(0);
                lastResetTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            logManager.logToFile("ERROR", "Failed to load stats: " + e.getMessage(), null);
        }
    }

    /**
     * Save statistics to file.
     */
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
}