package org.ayosynk.antiRedstoneLag;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CounterManager {
    private final Map<Chunk, Integer> chunkCounters = new HashMap<>();
    private final Map<Location, Integer> blockCounters = new HashMap<>();
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;

    // Statistics
    private final AtomicInteger totalRemovals = new AtomicInteger(0);
    private final AtomicInteger removalsToday = new AtomicInteger(0);
    private long lastResetTime = System.currentTimeMillis();

    public CounterManager(ConfigManager configManager, MessageManager messageManager, LogManager logManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
    }

    public void incrementCounters(Chunk chunk, Location location) {
        chunkCounters.put(chunk, chunkCounters.getOrDefault(chunk, 0) + 1);
        blockCounters.put(location, blockCounters.getOrDefault(location, 0) + 1);
    }

    public boolean shouldDisable(Chunk chunk, Location location) {
        return chunkCounters.getOrDefault(chunk, 0) > configManager.getChunkThreshold() &&
                blockCounters.getOrDefault(location, 0) > configManager.getBlockThreshold();
    }

    public void resetCounters() {
        // Log performance stats before resetting
        if (configManager.isLogPerformance()) {
            logPerformanceStats();
        }

        chunkCounters.clear();
        blockCounters.clear();

        // Reset daily counter if it's a new day
        if (System.currentTimeMillis() - lastResetTime > 24 * 60 * 60 * 1000) {
            removalsToday.set(0);
            lastResetTime = System.currentTimeMillis();
        }
    }

    public void handleRedstoneRemoval(Location location, Material material) {
        Chunk chunk = location.getChunk();
        int chunkCount = chunkCounters.getOrDefault(chunk, 0);
        int blockCount = blockCounters.getOrDefault(location, 0);

        totalRemovals.incrementAndGet();
        removalsToday.incrementAndGet();

        // Log the removal
        logManager.logRedstoneRemoval(location, material, chunkCount, blockCount, "Exceeded thresholds");

        // Send alert if enabled
        if (configManager.isAlertsEnabled()) {
            sendAlert(location, material, chunkCount, blockCount);
        }
    }

    private void sendAlert(Location location, Material material, int chunkCount, int blockCount) {
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
        int chunksMonitored = chunkCounters.size();
        int blocksMonitored = blockCounters.size();
        double avgUpdates = chunkCounters.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);

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
        return chunkCounters.size();
    }

    public int getBlocksMonitored() {
        return blockCounters.size();
    }
}