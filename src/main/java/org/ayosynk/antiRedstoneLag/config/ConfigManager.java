package org.ayosynk.antiRedstoneLag.config;

import eu.okaeri.configs.serdes.commons.SerdesCommons;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

public class ConfigManager {
    public enum RemovalAction {
        REMOVE,
        DISABLE,
        DROP
    }

    private final JavaPlugin plugin;
    private PluginConfig pluginConfig;
    private Set<Material> redstoneMaterials;
    private Set<String> enabledWorlds;
    private Set<String> whitelistedChunks;

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

        this.redstoneMaterials = new HashSet<>(pluginConfig.getRedstoneComponents());
        this.enabledWorlds = new HashSet<>(pluginConfig.getEnabledWorlds());
        if (this.enabledWorlds.isEmpty()) {
            this.enabledWorlds.add("*");
        }
        this.whitelistedChunks = new HashSet<>(pluginConfig.getWhitelist().getChunks());
    }

    private void handleBackup(File file) {
        if (!file.exists()) return;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!content.contains("config-version: \"26.2\"") && !content.contains("config-version: '26.2'")) {
                File backupFile = new File(file.getParentFile(), file.getName() + ".bk");
                Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Created safety backup: " + file.getName() + " -> " + backupFile.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check or create backup for " + file.getName() + ": " + e.getMessage());
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

    public boolean isAlertsEnabled() {
        return pluginConfig.getAlerts().isEnabled();
    }

    public boolean isLogToConsole() {
        return pluginConfig.getAlerts().isLogToConsole();
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

    public ConfigManager.RemovalAction getRemovalAction() {
        PluginConfig.RemovalAction action = pluginConfig.getRemovalAction();
        if (action == PluginConfig.RemovalAction.DISABLE) return RemovalAction.DISABLE;
        if (action == PluginConfig.RemovalAction.DROP) return RemovalAction.DROP;
        return RemovalAction.REMOVE;
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

    public boolean isChunkWhitelisted(String chunkKey) {
        if (!isWhitelistEnabled()) return true;
        return whitelistedChunks.contains(chunkKey);
    }

    public Set<String> getWhitelistedChunks() {
        return whitelistedChunks;
    }
}