package org.ayosynk.antiRedstoneLag.manager;

import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;

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

public class CounterManager {
    private static final long ALERT_COOLDOWN_MS = 1000;
    private static final long WARNING_COOLDOWN_MS = 5000;
    private static final long DAY_MS = 24 * 60 * 60 * 1000;

    private final Object2IntOpenHashMap<String> chunkCounters = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<String> blockCounters = new Object2IntOpenHashMap<>();
    private final Map<String, Long> warnedPlayers = new ConcurrentHashMap<>();
    private final Object counterLock = new Object();
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;
    private final File statsFile;

    private final AtomicInteger totalRemovals = new AtomicInteger(0);
    private final AtomicInteger removalsToday = new AtomicInteger(0);
    private final AtomicLong lastAlertTime = new AtomicLong(0);
    private final AtomicLong lastWarningTime = new AtomicLong(0);
    private volatile long lastResetTime = System.currentTimeMillis();

    public CounterManager(ConfigManager configManager, MessageManager messageManager, LogManager logManager, File dataFolder) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
        this.statsFile = new File(dataFolder, "stats.yml");
        loadStats();
    }

    public void incrementCounters(String chunkKey, String blockKey) {
        if (chunkKey == null || blockKey == null) return;
        synchronized (counterLock) {
            chunkCounters.addTo(chunkKey, 1);
            blockCounters.addTo(blockKey, 1);
        }
    }

    public boolean shouldDisable(String chunkKey, String blockKey) {
        if (chunkKey == null || blockKey == null) return false;
        synchronized (counterLock) {
            int chunkCount = chunkCounters.getInt(chunkKey);
            int blockCount = blockCounters.getInt(blockKey);
            return (chunkCount > configManager.getChunkThreshold()) &&
                    (blockCount > configManager.getBlockThreshold());
        }
    }

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

    public void sendWarning(Location location, Material material, java.util.UUID ownerUuid) {
        if (location == null || location.getWorld() == null) return;
        if (!canSendWarning()) return;

        String chunkKey = location.getWorld().getName() + ":" + (location.getBlockX() >> 4) + ":" + (location.getBlockZ() >> 4);
        int currentCount;
        synchronized (counterLock) {
            currentCount = chunkCounters.getInt(chunkKey);
        }
        int threshold = configManager.getChunkThreshold();
        int percent = (currentCount * 100) / threshold;

        if (ownerUuid != null) {
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (owner != null && owner.isOnline()) {
                Long lastWarned = warnedPlayers.get(ownerUuid.toString());
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
        if (configManager.isLogPerformance()) {
            logPerformanceStats();
        }

        synchronized (counterLock) {
            chunkCounters.clear();
            blockCounters.clear();
        }

        warnedPlayers.clear();

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
        int chunksMonitored, blocksMonitored;
        double avgUpdates;
        synchronized (counterLock) {
            chunksMonitored = chunkCounters.size();
            blocksMonitored = blockCounters.size();
            avgUpdates = chunkCounters.values().intStream().average().orElse(0.0);
        }
        logManager.logPerformanceStats(chunksMonitored, blocksMonitored, avgUpdates);
    }

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
}