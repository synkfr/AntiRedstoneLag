package org.ayosynk.antiRedstoneLag.manager;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.engine.AdaptiveMonitor;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

/**
 * Registers bStats telemetry charts (plugin ID 27839).
 */
public class MetricsManager {
    private static final int PLUGIN_ID = 27839;

    public MetricsManager(AntiRedstoneLag plugin) {
        try {
            Metrics metrics = new Metrics(plugin, PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("redstone_mode", () -> {
                if (plugin.getConfigManager() == null) return "unknown";
                if (!plugin.getConfigManager().isRedstoneEnabled()) return "disabled";
                return plugin.getConfigManager().getRemovalAction().name().toLowerCase();
            }));
            metrics.addCustomChart(new SimplePie("storage_backend", () ->
                    plugin.getStorageService() != null ? plugin.getStorageService().backendName() : "unknown"));
            metrics.addCustomChart(new SimplePie("server_platform", () ->
                    AdaptiveMonitor.isFolia() ? "Folia" : "Paper"));
            plugin.getLogger().info("Metrics initialized successfully!");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize metrics: " + e.getMessage());
        }
    }
}
