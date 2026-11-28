package org.ayosynk.antiRedstoneLag.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.engine.AdaptiveMonitor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optional PlaceholderAPI expansion exposing live AntiRedstoneLag metrics and toggles.
 */
public class PapiExpansion extends PlaceholderExpansion {
    private final AntiRedstoneLag plugin;

    public PapiExpansion(AntiRedstoneLag plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "arl";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AyoSynk";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        var config = plugin.getConfigManager();
        var engine = plugin.getRedstoneEngine();
        var clear = plugin.getClearLaggManager();

        return switch (params.toLowerCase()) {
            case "status" -> config.isRedstoneEnabled() ? "ON" : "OFF";
            case "mode" -> config.isRedstoneEnabled() ? config.getRemovalAction().name() : "OFF";
            case "alerts" -> config.isAlertsEnabled() ? "ON" : "OFF";
            case "tps" -> AdaptiveMonitor.isFolia() ? "N/A"
                    : String.format("%.1f", engine.getAdaptiveMonitor().getCachedTps());
            case "mspt" -> AdaptiveMonitor.isFolia() ? "N/A"
                    : String.format("%.1f", engine.getAdaptiveMonitor().getCachedMspt());
            case "chunks_monitored" -> String.valueOf(engine.getChunksMonitored());
            case "blocks_monitored" -> String.valueOf(engine.getBlocksMonitored());
            case "frozen_count" -> String.valueOf(engine.getActiveFreezeCount());
            case "locked_count" -> String.valueOf(engine.getActiveLockdownCount());
            case "removals_total" -> String.valueOf(engine.getStats().getTotalRemovals());
            case "removals_today" -> String.valueOf(engine.getStats().getRemovalsToday());
            case "freezes_total" -> String.valueOf(engine.getStats().getTotalFreezes());
            case "lockdowns_total" -> String.valueOf(engine.getStats().getTotalLockdowns());
            case "entities_cleared" -> String.valueOf(engine.getStats().getTotalEntitiesCleared());
            case "clear_countdown" -> clear != null ? String.valueOf(clear.getSecondsUntilClear()) : "0";
            default -> null;
        };
    }
}
