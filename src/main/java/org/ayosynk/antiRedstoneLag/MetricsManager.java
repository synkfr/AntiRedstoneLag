package org.ayosynk.antiRedstoneLag;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

public class MetricsManager {
    private final JavaPlugin plugin;
    private final int pluginId = 27839; // replace with your bStats plugin ID

    public MetricsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        try {
            Metrics metrics = new Metrics(plugin, pluginId);
            setupCustomCharts(metrics);
            plugin.getLogger().info("Metrics initialized successfully!");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize metrics: " + e.getMessage());
        }
    }

    private void setupCustomCharts(Metrics metrics) {
        metrics.addCustomChart(new SimplePie("redstone_components_monitored", () -> "enabled"));
    }
}
