package org.ayosynk.antiRedstoneLag;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigManager {
    private final JavaPlugin plugin;
    private int chunkThreshold;
    private int blockThreshold;
    private boolean alertsEnabled;
    private boolean logToConsole;
    private boolean logPerformance;
    private Set<String> enabledWorlds;
    private Set<Material> redstoneMaterials;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        chunkThreshold = config.getInt("chunk-threshold", 500);
        blockThreshold = config.getInt("block-threshold", 15);
        alertsEnabled = config.getBoolean("alerts.enabled", true);
        logToConsole = config.getBoolean("alerts.log-to-console", true);
        logPerformance = config.getBoolean("logging.performance-stats", true);

        // World settings
        enabledWorlds = new HashSet<>();
        List<String> worlds = config.getStringList("enabled-worlds");
        if (worlds.isEmpty()) {
            // If no worlds specified, enable for all worlds
            enabledWorlds.add("*");
        } else {
            enabledWorlds.addAll(worlds);
        }

        redstoneMaterials = new HashSet<>();
        for (String materialName : config.getStringList("redstone-components")) {
            try {
                redstoneMaterials.add(Material.valueOf(materialName));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material: " + materialName);
            }
        }
    }

    public int getChunkThreshold() {
        return chunkThreshold;
    }

    public int getBlockThreshold() {
        return blockThreshold;
    }

    public boolean isAlertsEnabled() {
        return alertsEnabled;
    }

    public boolean isLogToConsole() {
        return logToConsole;
    }

    public boolean isLogPerformance() {
        return logPerformance;
    }

    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.contains("*") || enabledWorlds.contains(worldName);
    }

    public Set<Material> getRedstoneMaterials() {
        return redstoneMaterials;
    }
}