package org.ayosynk.antiRedstoneLag.config;

import eu.okaeri.configs.serdes.commons.SerdesCommons;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ayosynk.antiRedstoneLag.manager.CounterManager;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigManager {
    private final JavaPlugin plugin;
    private PluginConfig pluginConfig;
    private Set<Material> redstoneMaterials;
    private Set<String> enabledWorlds;
    private Map<String, LongOpenHashSet> whitelistedChunks;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        handleBackup(configFile);

        this.pluginConfig = eu.okaeri.configs.ConfigManager.create(PluginConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer(), new SerdesBukkit(), new SerdesCommons());
            it.withBindFile(configFile);
            it.withRemoveOrphans(true);
            it.saveDefaults();
            it.load(true);
        });

        if (!"26.5".equals(pluginConfig.getConfigVersion())) {
            pluginConfig.setConfigVersion("26.5");
            pluginConfig.save();
        }

        this.redstoneMaterials = new HashSet<>(pluginConfig.getRedstoneComponents());
        this.enabledWorlds = new HashSet<>(pluginConfig.getEnabledWorlds());
        if (this.enabledWorlds.isEmpty()) {
            this.enabledWorlds.add("*");
        }
        this.whitelistedChunks = parseWhitelistedChunks(pluginConfig.getWhitelist().getChunks(), plugin);
    }

    public static Map<String, LongOpenHashSet> parseWhitelistedChunks(List<String> entries, JavaPlugin loggerPlugin) {
        Map<String, LongOpenHashSet> map = new HashMap<>();
        if (entries == null) return map;
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.trim().split(":");
            if (parts.length != 3) {
                if (loggerPlugin != null) {
                    loggerPlugin.getLogger().warning("Invalid whitelist chunk entry (expected world:chunkX:chunkZ): " + entry);
                }
                continue;
            }
            try {
                int cx = Integer.parseInt(parts[1].trim());
                int cz = Integer.parseInt(parts[2].trim());
                map.computeIfAbsent(parts[0].trim(), k -> new LongOpenHashSet())
                   .add(CounterManager.packChunk(cx, cz));
            } catch (NumberFormatException e) {
                if (loggerPlugin != null) {
                    loggerPlugin.getLogger().warning("Invalid chunk coordinates in whitelist entry: " + entry);
                }
            }
        }
        return map;
    }

    private void handleBackup(File file) {
        if (!file.exists()) return;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!content.contains("config-version: \"26.5\"") && !content.contains("config-version: '26.5'")) {
                File backupFile = new File(file.getParentFile(), file.getName() + ".bk");
                Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Created safety backup: " + file.getName() + " -> " + backupFile.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check or create backup for " + file.getName() + ": " + e.getMessage());
        }
    }

    public void saveConfig() {
        if (pluginConfig != null) {
            pluginConfig.save();
        }
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public int getChunkThreshold() {
        return pluginConfig.getChunkThreshold();
    }

    public int getBlockThreshold() {
        return pluginConfig.getBlockThreshold();
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

    public PluginConfig.RemovalAction getRemovalAction() {
        return pluginConfig.getRemovalAction();
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
        return set != null && set.contains(CounterManager.packChunk(chunkX, chunkZ));
    }
}