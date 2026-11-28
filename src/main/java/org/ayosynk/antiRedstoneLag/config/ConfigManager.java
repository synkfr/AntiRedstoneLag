package org.ayosynk.antiRedstoneLag.config;

import eu.okaeri.configs.serdes.commons.SerdesCommons;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ayosynk.antiRedstoneLag.util.BlockKeyCodec;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Manages loading, reloading, v1->v2 migration, and indexed caches for config.yml.
 */
public class ConfigManager {
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final Logger logger;

    private PluginConfig pluginConfig;
    private volatile Set<Material> redstoneMaterials = Set.of();
    private volatile Set<String> enabledWorlds = Set.of("*");
    private volatile Map<String, LongOpenHashSet> whitelistedChunks = new HashMap<>();

    private File configFile;

    public ConfigManager(JavaPlugin plugin) {
        this(plugin, plugin.getDataFolder(), plugin.getLogger());
    }

    public ConfigManager(JavaPlugin plugin, File dataFolder, Logger logger) {
        this.plugin = plugin;
        this.dataFolder = dataFolder;
        this.logger = logger;
        reloadConfig();
    }

    public void reloadConfig() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        this.configFile = new File(dataFolder, "config.yml");
        YamlConfiguration oldConfig = null;
        if (ConfigMigrator.needsMigration(configFile)) {
            try {
                oldConfig = ConfigMigrator.backupAndReadOld(configFile, logger);
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("Failed to back up v1 config.yml: " + e.getMessage());
                }
            }
        }

        this.pluginConfig = eu.okaeri.configs.ConfigManager.create(PluginConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer(), new SerdesCommons());
            it.withBindFile(configFile);
            it.withRemoveOrphans(true);
        });

        if (configFile.exists()) {
            try (InputStream in = Files.newInputStream(configFile.toPath())) {
                this.pluginConfig.load(in);
            } catch (IOException e) {
                if (logger != null) {
                    logger.warning("Failed to load config.yml: " + e.getMessage());
                }
            }
        }

        if (oldConfig != null) {
            ConfigMigrator.applyCompatibleSettings(oldConfig, this.pluginConfig, logger);
        } else if (pluginConfig.getConfigVersion() != ConfigMigrator.CURRENT_CONFIG_VERSION) {
            pluginConfig.setConfigVersion(ConfigMigrator.CURRENT_CONFIG_VERSION);
        }
        saveConfig();

        rebuildCaches();
    }

    private void rebuildCaches() {
        this.redstoneMaterials = new HashSet<>(pluginConfig.getRedstoneComponents());
        Set<String> worlds = new HashSet<>(pluginConfig.getEnabledWorlds());
        if (worlds.isEmpty()) {
            worlds.add("*");
        }
        this.enabledWorlds = Set.copyOf(worlds);
        this.whitelistedChunks = parseWhitelistedChunks(pluginConfig.getWhitelist().getChunks(), logger);
    }

    public static Map<String, LongOpenHashSet> parseWhitelistedChunks(List<String> entries, Logger logger) {
        Map<String, LongOpenHashSet> map = new HashMap<>();
        if (entries == null) return map;
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.trim().split(":");
            if (parts.length != 3) {
                if (logger != null) {
                    logger.warning("Invalid whitelist chunk entry (expected world:chunkX:chunkZ): " + entry);
                }
                continue;
            }
            try {
                int cx = Integer.parseInt(parts[1].trim());
                int cz = Integer.parseInt(parts[2].trim());
                map.computeIfAbsent(parts[0].trim(), k -> new LongOpenHashSet())
                        .add(BlockKeyCodec.packChunk(cx, cz));
            } catch (NumberFormatException e) {
                if (logger != null) {
                    logger.warning("Invalid chunk coordinates in whitelist entry: " + entry);
                }
            }
        }
        return map;
    }

    public synchronized boolean addWhitelistedChunk(String worldName, int chunkX, int chunkZ) {
        String key = worldName + ":" + chunkX + ":" + chunkZ;
        List<String> chunks = new ArrayList<>(pluginConfig.getWhitelist().getChunks());
        if (chunks.contains(key)) {
            return false;
        }
        chunks.add(key);
        pluginConfig.getWhitelist().setChunks(chunks);
        saveConfig();
        this.whitelistedChunks = parseWhitelistedChunks(chunks, logger);
        return true;
    }

    public synchronized boolean removeWhitelistedChunk(String worldName, int chunkX, int chunkZ) {
        String key = worldName + ":" + chunkX + ":" + chunkZ;
        List<String> chunks = new ArrayList<>(pluginConfig.getWhitelist().getChunks());
        if (!chunks.remove(key)) {
            return false;
        }
        pluginConfig.getWhitelist().setChunks(chunks);
        saveConfig();
        this.whitelistedChunks = parseWhitelistedChunks(chunks, logger);
        return true;
    }

    public List<String> getWhitelistedChunksList() {
        return List.copyOf(pluginConfig.getWhitelist().getChunks());
    }

    public void saveConfig() {
        if (pluginConfig != null && configFile != null) {
            try (OutputStream out = Files.newOutputStream(configFile.toPath())) {
                pluginConfig.save(out);
            } catch (IOException e) {
                if (logger != null) {
                    logger.warning("Failed to save config.yml: " + e.getMessage());
                }
            }
        }
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public int getChunkThreshold() {
        return pluginConfig.getChunkThreshold();
    }

    public int getChunkThreshold(String worldName) {
        PluginConfig.WorldOverrideConfig override = pluginConfig.getWorlds().get(worldName);
        if (override != null && override.getChunkThreshold() != null) {
            return Math.max(1, override.getChunkThreshold());
        }
        return getChunkThreshold();
    }

    public int getBlockThreshold() {
        return pluginConfig.getBlockThreshold();
    }

    public int getBlockThreshold(String worldName) {
        PluginConfig.WorldOverrideConfig override = pluginConfig.getWorlds().get(worldName);
        if (override != null && override.getBlockThreshold() != null) {
            return Math.max(1, override.getBlockThreshold());
        }
        return getBlockThreshold();
    }

    public PluginConfig.RemovalAction getRemovalAction() {
        return pluginConfig.getRemovalAction();
    }

    public PluginConfig.RemovalAction getRemovalAction(String worldName) {
        PluginConfig.WorldOverrideConfig override = pluginConfig.getWorlds().get(worldName);
        if (override != null && override.getRemovalAction() != null) {
            return override.getRemovalAction();
        }
        return getRemovalAction();
    }

    public boolean isClearLaggEnabledInWorld(String worldName) {
        PluginConfig.WorldOverrideConfig override = pluginConfig.getWorlds().get(worldName);
        if (override != null && override.getClearlaggEnabled() != null) {
            return override.getClearlaggEnabled();
        }
        return true;
    }

    public boolean isRedstoneEnabled() {
        return pluginConfig.isRedstoneEnabled();
    }

    public void setRedstoneEnabled(boolean enabled) {
        pluginConfig.setRedstoneEnabled(enabled);
        saveConfig();
    }

    public void setRemovalAction(PluginConfig.RemovalAction action) {
        pluginConfig.setRemovalAction(action);
        saveConfig();
    }

    public boolean isAlertsEnabled() {
        return pluginConfig.getAlerts().isEnabled();
    }

    public void setAlertsEnabled(boolean enabled) {
        pluginConfig.getAlerts().setEnabled(enabled);
        saveConfig();
    }

    public boolean isLogToConsole() {
        return pluginConfig.getAlerts().isLogToConsole();
    }

    public boolean isNotifyOnTpsLag() {
        return pluginConfig.getAlerts().isNotifyOnTpsLag();
    }

    public boolean isOnlyWhenLagging() {
        return pluginConfig.getAlerts().isOnlyWhenLagging();
    }

    public boolean isLogPerformance() {
        return pluginConfig.getLogging().isPerformanceStats();
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
        return pluginConfig.isDebug();
    }

    public int getResetInterval() {
        return pluginConfig.getResetIntervalTicks();
    }

    public boolean isLockdownEnabled() {
        return pluginConfig.getLockdown().isEnabled();
    }

    public int getLockdownDurationSeconds() {
        return pluginConfig.getLockdown().getDurationSeconds();
    }

    public boolean isWarningEnabled() {
        return pluginConfig.getWarning().isEnabled();
    }

    public int getWarningThresholdPercent() {
        return pluginConfig.getWarning().getThresholdPercent();
    }

    public int getChunkWarningThreshold() {
        return (getChunkThreshold() * getWarningThresholdPercent()) / 100;
    }

    public int getBlockWarningThreshold() {
        return (getBlockThreshold() * getWarningThresholdPercent()) / 100;
    }

    public boolean isWhitelistEnabled() {
        return pluginConfig.getWhitelist().isEnabled();
    }

    public boolean isChunkWhitelisted(String worldName, int chunkX, int chunkZ) {
        if (!isWhitelistEnabled()) return true;
        LongOpenHashSet set = whitelistedChunks.get(worldName);
        return set != null && set.contains(BlockKeyCodec.packChunk(chunkX, chunkZ));
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
