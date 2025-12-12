package org.ayosynk.antiRedstoneLag;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages plugin configuration loaded from config.yml.
 * Provides access to all configurable settings with validation.
 */
public class ConfigManager {
    /**
     * Removal action options for redstone that exceeds thresholds.
     */
    public enum RemovalAction {
        REMOVE,  // Set block to air
        DISABLE, // Cancel the redstone signal (don't remove block)
        DROP     // Break block and drop item
    }

    private final JavaPlugin plugin;
    private int chunkThreshold;
    private int blockThreshold;
    private boolean alertsEnabled;
    private boolean logToConsole;
    private boolean logPerformance;
    private boolean debugMode;
    private int resetInterval;
    private RemovalAction removalAction;
    private boolean warningEnabled;
    private int warningThresholdPercent;
    private boolean whitelistEnabled;
    private Set<String> whitelistedChunks;
    private Set<String> enabledWorlds;
    private Set<Material> redstoneMaterials;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        chunkThreshold = Math.max(1, config.getInt("chunk-threshold", 500));
        blockThreshold = Math.max(1, config.getInt("block-threshold", 15));
        alertsEnabled = config.getBoolean("alerts.enabled", true);
        logToConsole = config.getBoolean("alerts.log-to-console", true);
        logPerformance = config.getBoolean("logging.performance-stats", true);
        debugMode = config.getBoolean("debug", false);
        resetInterval = Math.max(1, config.getInt("reset-interval-ticks", 20));

        // Parse removal action
        String actionStr = config.getString("removal-action", "REMOVE").toUpperCase();
        try {
            removalAction = RemovalAction.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid removal-action '" + actionStr + "', defaulting to REMOVE");
            removalAction = RemovalAction.REMOVE;
        }

        // Warning system
        warningEnabled = config.getBoolean("warning.enabled", true);
        warningThresholdPercent = Math.max(1, Math.min(99, config.getInt("warning.threshold-percent", 80)));

        // Whitelist mode
        whitelistEnabled = config.getBoolean("whitelist.enabled", false);
        whitelistedChunks = new HashSet<>(config.getStringList("whitelist.chunks"));

        if (debugMode) {
            plugin.getLogger().info("[DEBUG] Config loaded: chunk-threshold=" + chunkThreshold + ", block-threshold=" + blockThreshold);
        }

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

    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public int getResetInterval() {
        return resetInterval;
    }

    public RemovalAction getRemovalAction() {
        return removalAction;
    }

    public boolean isWarningEnabled() {
        return warningEnabled;
    }

    public int getWarningThresholdPercent() {
        return warningThresholdPercent;
    }

    /**
     * Calculate the warning threshold for chunks.
     */
    public int getChunkWarningThreshold() {
        return (chunkThreshold * warningThresholdPercent) / 100;
    }

    /**
     * Calculate the warning threshold for blocks.
     */
    public int getBlockWarningThreshold() {
        return (blockThreshold * warningThresholdPercent) / 100;
    }

    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }

    /**
     * Check if a chunk is whitelisted for monitoring.
     * @param chunkKey The chunk key in format "world:chunkX:chunkZ"
     * @return true if whitelist is disabled OR chunk is in whitelist
     */
    public boolean isChunkWhitelisted(String chunkKey) {
        if (!whitelistEnabled) return true; // If whitelist disabled, all chunks are allowed
        return whitelistedChunks.contains(chunkKey);
    }

    public Set<String> getWhitelistedChunks() {
        return whitelistedChunks;
    }
}