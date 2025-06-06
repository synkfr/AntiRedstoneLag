package org.ayosynk.antiRedstoneLag;

import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

public class CounterManager {
    private final Map<Chunk, Integer> chunkCounters = new HashMap<>();
    private final Map<Location, Integer> blockCounters = new HashMap<>();
    private final ConfigManager configManager;

    public CounterManager(ConfigManager configManager) {
        this.configManager = configManager;
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
        chunkCounters.clear();
        blockCounters.clear();
    }
}