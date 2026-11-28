package org.ayosynk.antiRedstoneLag.engine;

import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;

/**
 * Resolved per-world thresholds and mitigation action.
 */
public record WorldRuleSet(
        boolean enabled,
        int chunkThreshold,
        int blockThreshold,
        PluginConfig.RemovalAction removalAction
) {
    public static WorldRuleSet resolve(ConfigManager configManager, String worldName) {
        boolean enabled = configManager.isRedstoneEnabled() && configManager.isWorldEnabled(worldName);
        int chunkThreshold = configManager.getChunkThreshold(worldName);
        int blockThreshold = configManager.getBlockThreshold(worldName);
        PluginConfig.RemovalAction action = configManager.getRemovalAction(worldName);
        return new WorldRuleSet(enabled, chunkThreshold, blockThreshold, action);
    }
}
